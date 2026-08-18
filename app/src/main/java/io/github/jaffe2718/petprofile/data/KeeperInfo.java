package io.github.jaffe2718.petprofile.data;

public class KeeperInfo {
    public String nickname = "";
    public String homePlace = "";
    public Double latitude;
    public Double longitude;

    public boolean hasNickname() {
        return nickname != null && !nickname.trim().isEmpty();
    }

    public boolean hasHomePlace() {
        return homePlace != null && !homePlace.trim().isEmpty();
    }
}
