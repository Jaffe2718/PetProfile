package io.github.jaffe2718.petprofile.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.LocationHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

public class MapPickerActivity extends AppCompatActivity {
    public static final String EXTRA_INITIAL_LATITUDE = "initial_latitude";
    public static final String EXTRA_INITIAL_LONGITUDE = "initial_longitude";
    public static final String EXTRA_RESULT_LATITUDE = "result_latitude";
    public static final String EXTRA_RESULT_LONGITUDE = "result_longitude";

    private WebView webView;
    private Spinner mapProviderSpinner;
    private int selectedProvider;
    private double pendingGpsLat = Double.NaN;
    private double pendingGpsLng = Double.NaN;
    private EditText searchEditText;
    private View searchButton;
    private boolean searchInFlight;
    private long lastSearchAtMs;

    private static final long SEARCH_THROTTLE_MS = 1000L;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        double[] lastKnown = LocationHelper.lastKnownCoordinates(this);
        double initialLatitude = lastKnown != null
                ? lastKnown[0]
                : getIntent().getDoubleExtra(EXTRA_INITIAL_LATITUDE, 35.0);
        double initialLongitude = lastKnown != null
                ? lastKnown[1]
                : getIntent().getDoubleExtra(EXTRA_INITIAL_LONGITUDE, 105.0);

        webView = findViewById(R.id.mapWebView);
        mapProviderSpinner = findViewById(R.id.mapProviderSpinner);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyProvider(selectedProvider);
                applyPendingGps();
            }
        });
        webView.addJavascriptInterface(new MapBridge(), "AndroidBridge");
        webView.loadDataWithBaseURL(
                "https://unpkg.com/",
                buildMapHtml(initialLatitude, initialLongitude),
                "text/html",
                "utf-8",
                null
        );

        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        getString(R.string.map_provider_amap),
                        getString(R.string.map_provider_google),
                        getString(R.string.map_provider_osm)
                }
        );
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapProviderSpinner.setAdapter(providerAdapter);
        mapProviderSpinner.setSelection(selectedProvider);
        mapProviderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                selectedProvider = position;
                applyProvider(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Button confirmButton = findViewById(R.id.confirmButton);
        confirmButton.setOnClickListener(v -> webView.evaluateJavascript("chooseLocation()", null));

        setupSearch();
        requestCurrentMapLocation();
    }

    private void applyProvider(int position) {
        String provider = providerName(position);
        webView.evaluateJavascript("setMapProvider('" + provider + "')", null);
    }

    private String providerName(int position) {
        switch (position) {
            case 0:
                return "AMAP";
            case 1:
                return "GOOGLE";
            case 2:
                return "OSM";
            default:
                return "AMAP";
        }
    }

    private void requestCurrentMapLocation() {
        LocationHelper.request(this, new LocationHelper.Callback() {
            @Override
            public void onResult(LocationHelper.LocationResult result) {
                pendingGpsLat = result.latitude;
                pendingGpsLng = result.longitude;
                moveMapMarker(pendingGpsLat, pendingGpsLng);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void applyPendingGps() {
        if (!Double.isNaN(pendingGpsLat) && !Double.isNaN(pendingGpsLng)) {
            moveMapMarker(pendingGpsLat, pendingGpsLng);
        }
    }

    private void moveMapMarker(double latitude, double longitude) {
        webView.evaluateJavascript(
                "moveMarker(" + latitude + "," + longitude + ")",
                null
        );
    }

    private void setupSearch() {
        searchEditText = findViewById(R.id.mapSearchEditText);
        searchButton = findViewById(R.id.mapSearchButton);
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> performAddressSearch());
        }
        if (searchEditText != null) {
            searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP)) {
                    performAddressSearch();
                    return true;
                }
                return false;
            });
        }
    }

    private void performAddressSearch() {
        if (searchInFlight || searchEditText == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSearchAtMs < SEARCH_THROTTLE_MS) {
            return;
        }
        String query = searchEditText.getText() == null
                ? ""
                : searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        searchInFlight = true;
        lastSearchAtMs = now;
        hideKeyboard();
        Async.run(() -> {
            double[] coord = null;
            String error = null;
            try {
                coord = geocode(query);
            } catch (Throwable t) {
                error = t.getMessage();
            }
            final double[] result = coord;
            final String err = error;
            Async.ui(() -> {
                searchInFlight = false;
                if (result != null && result.length >= 2) {
                    moveMapMarker(result[0], result[1]);
                } else if (err != null) {
                    Toast.makeText(MapPickerActivity.this,
                            R.string.map_search_failed, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MapPickerActivity.this,
                            R.string.map_search_no_result, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private double[] geocode(String query) throws Exception {
        String url = "https://nominatim.openstreetmap.org/search?q="
                + URLEncoder.encode(query, "UTF-8")
                + "&format=jsonv2&limit=1";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "PetProfile/0.2.3 (Android; address-search)");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                JSONArray array = new JSONArray(builder.toString());
                if (array.length() == 0) {
                    return null;
                }
                JSONObject first = array.getJSONObject(0);
                double lat = Double.parseDouble(first.getString("lat"));
                double lon = Double.parseDouble(first.getString("lon"));
                return new double[]{lat, lon};
            }
        } finally {
            connection.disconnect();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @androidx.annotation.NonNull String[] permissions,
                                           @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LocationHelper.REQUEST_LOCATION
                && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestCurrentMapLocation();
        }
    }

    private String buildMapHtml(double latitude, double longitude) {
        return "<!DOCTYPE html><html><head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />"
                + "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css' />"
                + "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"
                + "<style>html,body,#map{height:100%;margin:0;}</style>"
                + "</head><body><div id='map'></div><script>"
                + "var initialLat = " + latitude + ";"
                + "var initialLng = " + longitude + ";"
                + "var wgsLat = initialLat;"
                + "var wgsLng = initialLng;"
                + "var map = L.map('map').setView([initialLat, initialLng], 13);"
                + "var tileLayer = null;"
                + "var marker = L.marker([initialLat, initialLng], {draggable:true}).addTo(map);"
                + "var currentProvider = 'AMAP';"
                + "var PI = 3.1415926535897932384626;"
                + "var A = 6378245.0;"
                + "var EE = 0.00669342162296594323;"
                + "function outOfChina(lat,lng){return lng<72.004||lng>137.8347||lat<0.8293||lat>55.8271;}"
                + "function transformLat(x,y){var ret=-100.0+2.0*x+3.0*y+0.2*y*y+0.1*x*y+0.2*Math.sqrt(Math.abs(x));ret+=(20.0*Math.sin(6.0*x*PI)+20.0*Math.sin(2.0*x*PI))*2.0/3.0;ret+=(20.0*Math.sin(y*PI)+40.0*Math.sin(y/3.0*PI))*2.0/3.0;ret+=(160.0*Math.sin(y/12.0*PI)+320*Math.sin(y*PI/30.0))*2.0/3.0;return ret;}"
                + "function transformLng(x,y){var ret=300.0+x+2.0*y+0.1*x*x+0.1*x*y+0.1*Math.sqrt(Math.abs(x));ret+=(20.0*Math.sin(6.0*x*PI)+20.0*Math.sin(2.0*x*PI))*2.0/3.0;ret+=(20.0*Math.sin(x*PI)+40.0*Math.sin(x/3.0*PI))*2.0/3.0;ret+=(150.0*Math.sin(x/12.0*PI)+300.0*Math.sin(x/30.0*PI))*2.0/3.0;return ret;}"
                + "function wgs84ToGcj02(lat,lng){if(outOfChina(lat,lng)){return {lat:lat,lng:lng};}var dLat=transformLat(lng-105.0,lat-35.0);var dLng=transformLng(lng-105.0,lat-35.0);var radLat=lat/180.0*PI;var magic=Math.sin(radLat);magic=1-EE*magic*magic;var sqrtMagic=Math.sqrt(magic);dLat=(dLat*180.0)/((A*(1-EE))/(magic*sqrtMagic)*PI);dLng=(dLng*180.0)/(A/sqrtMagic*Math.cos(radLat)*PI);return {lat:lat+dLat,lng:lng+dLng};}"
                + "function gcj02ToWgs84(lat,lng){if(outOfChina(lat,lng)){return {lat:lat,lng:lng};}var g=wgs84ToGcj02(lat,lng);return {lat:lat*2-g.lat,lng:lng*2-g.lng};}"
                + "function displayPos(){if(currentProvider==='AMAP'){var g=wgs84ToGcj02(wgsLat,wgsLng);return [g.lat,g.lng];}return [wgsLat,wgsLng];}"
                + "function updateMapPosition(){var p=displayPos();map.setView(p,map.getZoom());marker.setLatLng(p);}"
                + "function setMapProvider(name){currentProvider=name;if(tileLayer){map.removeLayer(tileLayer);}var url='';var options={maxZoom:19};"
                + "if(name==='AMAP'){url='https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}';options.subdomains=['1','2','3','4'];} "
                + "else if(name==='GOOGLE'){url='https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}';} "
                + "else{url='https://tile.openstreetmap.org/{z}/{x}/{y}.png';}"
                + "tileLayer=L.tileLayer(url,options).addTo(map);updateMapPosition();}"
                + "function moveMarker(lat,lng){wgsLat=lat;wgsLng=lng;updateMapPosition();}"
                + "map.on('click', function(e){var pos=e.latlng;if(currentProvider==='AMAP'){var w=gcj02ToWgs84(pos.lat,pos.lng);wgsLat=w.lat;wgsLng=w.lng;}else{wgsLat=pos.lat;wgsLng=pos.lng;}marker.setLatLng(pos);});"
                + "marker.on('dragend', function(){var pos=marker.getLatLng();if(currentProvider==='AMAP'){var w=gcj02ToWgs84(pos.lat,pos.lng);wgsLat=w.lat;wgsLng=w.lng;}else{wgsLat=pos.lat;wgsLng=pos.lng;}});"
                + "function chooseLocation(){AndroidBridge.onLocation(wgsLat, wgsLng);}"
                + "setMapProvider('AMAP');"
                + "</script></body></html>";
    }

    private final class MapBridge {
        @JavascriptInterface
        public void onLocation(double latitude, double longitude) {
            runOnUiThread(() -> {
                Intent data = new Intent();
                data.putExtra(EXTRA_RESULT_LATITUDE, latitude);
                data.putExtra(EXTRA_RESULT_LONGITUDE, longitude);
                setResult(RESULT_OK, data);
                finish();
            });
        }
    }
}
