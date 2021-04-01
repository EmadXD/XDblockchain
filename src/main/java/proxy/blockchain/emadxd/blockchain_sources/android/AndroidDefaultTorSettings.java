package proxy.blockchain.emadxd.blockchain_sources.android;

import android.content.Context;
import proxy.blockchain.emadxd.blockchain_sources.DefaultSettings;

public class AndroidDefaultTorSettings extends DefaultSettings {

    private final Context context;

    public AndroidDefaultTorSettings(Context context) {
        this.context = context;
    }

    @Override
    public String getSocksPort() {
        return "1400";
    }

    @Override
    public boolean runAsDaemon() {
        return true;
    }

    @Override
    public String transPort() {
        return "auto";
    }

    @Override
    public boolean hasCookieAuthentication() {
        return true;
    }
}
