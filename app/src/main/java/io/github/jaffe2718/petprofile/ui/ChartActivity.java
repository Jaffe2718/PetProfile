package io.github.jaffe2718.petprofile.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.NumericPoint;
import io.github.jaffe2718.petprofile.data.NumericSeries;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;

import java.util.ArrayList;
import java.util.List;

public class ChartActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_ID = "profile_id";

    private PetRepository repository;
    private String profileId;
    private Spinner fieldSpinner;
    private LineChartView chartView;
    private TextView emptyTextView;
    private List<NumericSeries> seriesList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);
        repository = PetRepository.get(this);
        profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        if (profileId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        fieldSpinner = findViewById(R.id.fieldSpinner);
        chartView = findViewById(R.id.lineChartView);
        emptyTextView = findViewById(R.id.emptyTextView);

        fieldSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                showSeries(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        loadData();
    }

    private void loadData() {
        repository.getNumericChartData(profileId, new Async.Result<List<NumericSeries>>() {
            @Override
            public void onSuccess(List<NumericSeries> value) {
                seriesList = value;
                if (seriesList.isEmpty()) {
                    emptyTextView.setVisibility(android.view.View.VISIBLE);
                    fieldSpinner.setVisibility(android.view.View.GONE);
                    chartView.setVisibility(android.view.View.GONE);
                    return;
                }
                emptyTextView.setVisibility(android.view.View.GONE);
                fieldSpinner.setVisibility(android.view.View.VISIBLE);
                chartView.setVisibility(android.view.View.VISIBLE);
                List<String> labels = new ArrayList<>();
                for (NumericSeries series : seriesList) {
                    labels.add(series.fieldName + (series.unit == null || series.unit.isEmpty() ? "" : " (" + series.unit + ")"));
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        ChartActivity.this,
                        android.R.layout.simple_spinner_item,
                        labels
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                fieldSpinner.setAdapter(adapter);
                fieldSpinner.setSelection(0);
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(ChartActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showSeries(int position) {
        if (position < 0 || position >= seriesList.size()) {
            chartView.setData(new ArrayList<>(), "");
            return;
        }
        NumericSeries series = seriesList.get(position);
        List<ChartPoint> points = new ArrayList<>();
        for (NumericPoint point : series.points) {
            points.add(new ChartPoint(point.timestamp, point.value));
        }
        chartView.setData(points, series.unit == null ? "" : series.unit);
    }
}
