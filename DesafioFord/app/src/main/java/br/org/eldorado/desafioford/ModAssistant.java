package br.org.eldorado.desafioford;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;


import com.motorola.mod.IModManager;
import com.motorola.mod.ModDevice;
import com.motorola.mod.ModManager;
import com.motorola.mod.ModProtocol;
import com.motorola.mod.ModInterfaceDelegation;


/**
 * Created by wla015 on 08/09/2016.
 */
public class ModAssistant {

    private BroadcastReceiver modReceiver;
    private Context context;

    private static ModAssistant instance;

    private ModRawProtocol modRawProtocol;

    /**
     * ModManager interface
     */
    private ModManager modManager;

    /**
     * ModDevice interface
     */
    private ModDevice modDevice;

    public final static int ACTION_MOD_DETACH = 0;
    public final static int ACTION_MOD_ATTACH = 1;

    /** Bind with Moto Mod service */
    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            IModManager mMgrSrvc = IModManager.Stub.asInterface(binder);
            modManager = new ModManager(context, mMgrSrvc);

            Log.i(TAG, "onServiceConnected");
            onModAttach();
        }

        public void onServiceDisconnected(ComponentName className) {
            modDevice = null;
            modManager = null;
            Log.i(TAG, "onServiceDisconnected");
            onModDetach();
        }
    };


    private final String TAG = "ModAssistant";

    /**
     * Listeners to notify mod event and data
     */
    List<Handler> listeners = new ArrayList<>();

    public static boolean isSmartphoneCompatible(Context context) {
        return ModManager.isModServicesAvailable(context) ==  ModManager.SUCCESS;
    }


    public static ModAssistant getInstance(Context context) {
        if (instance == null) {
            instance = new ModAssistant(context);
        }
        return instance;
    }

    private ModAssistant (Context context) {

        this.context = context;

        /** Bind with Moto Mod service */
        Intent service = new Intent(ModManager.ACTION_BIND_MANAGER);
        service.setComponent(ModManager.MOD_SERVICE_NAME);
        context.bindService(service, mConnection, Context.BIND_AUTO_CREATE);

        /** Register Mod intents receiver */
        modReceiver = new MyBroadcastReceiver();
        IntentFilter filter = new IntentFilter(ModManager.ACTION_MOD_ATTACH);
        filter.addAction(ModManager.ACTION_MOD_DETACH);
        /**
         * Request the broadcaster who send these intents must hold permission PERMISSION_MOD_INTERNAL,
         * to avoid the intent from fake senders. For future details, refer to:
         * https://developer.android.com/reference/android/content/Context.html#registerReceiver
         */
        context.registerReceiver(modReceiver, filter, ModManager.PERMISSION_MOD_INTERNAL, null);

    }

    /** Clean up */
    public void onDestroy() {
        listeners.clear();
        context.unregisterReceiver(modReceiver);
        context.unbindService(mConnection);
    }

    public ModDevice getModDevice() {
        return modDevice;
    }

    public ModManager getModManager() {
        return modManager;
    }

    public void registerListener(Handler listener) {
        listeners.add(listener);
    }

    public void unregisterListener(Handler listener) {
        listeners.remove(listener);
    }


    private void notifyListeners(int what) {
        for (Handler handler : listeners) {
            handler.sendEmptyMessage(what);
        }
    }

    public void setModRawProtocol(ModRawProtocol mModRawProtocol) {
        modRawProtocol = mModRawProtocol;
        if (modRawProtocol != null) {
            if(modDevice != null) {
                modRawProtocol.onModAttach(modManager, modDevice);
            } else {
                modRawProtocol.onModDetach();
            }

        }
    }


    private void onModAttach() {
        try {
            /** Get currently mod device list from ModManager */
            List<ModDevice> l = modManager.getModList(false);
            if (l == null || l.size() == 0) {
                onModDetach();
                return;
            }

            // TODO: simply get first mod device from list for this example.
            // You may need consider to check expecting mod base on PID/VID or so on.
            for (ModDevice d : l) {
                if (d != null) {
                    modDevice = d;
                    if (modRawProtocol != null) {
                        modRawProtocol.onModAttach(modManager, modDevice);
                    }
                    notifyListeners(ACTION_MOD_ATTACH);
                }
            }
        } catch (RemoteException e) {
            onModDetach();
            e.printStackTrace();
        }
    }

    private void onModDetach() {
        modDevice = null;
        if (modRawProtocol != null) {
            modRawProtocol.onModDetach();
        }
        notifyListeners(ACTION_MOD_DETACH);
    }


    /** Handle mod device attach/detach event */
    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ModManager.ACTION_MOD_ATTACH.equals(action)) {
                /** Mod device attached */
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onModAttach();
                        Log.i(TAG, "ACTION_MOD_ATTACH");
                    }
                }, 1000);
            } else if (ModManager.ACTION_MOD_DETACH.equals(action)) {
                /** Mod device detached */
                onModDetach();
                Log.i(TAG, "ACTION_MOD_DETACH");
            }
        }
    }

    public String getText() {
        String res = "";
        try {

            List<ModDevice> l = modManager.getModList(false);

            for (ModDevice d : l) {
                res = res + d.getProductString() + " ";
                Log.i(TAG, "Device: " + d.getProductString());
                List<ModDevice.InterfaceInfo> is = d.getDeclaredInterfaces();
                for (ModDevice.InterfaceInfo i : is) {
                    Log.i(TAG, "InterfaceInfo: " + i.toString());
                }
                List<ModProtocol.Protocol> ps = d.getDeclaredProtocols();
                for (ModProtocol.Protocol p : ps) {
                    Log.i(TAG, "Protocol: " + p.toString());
                }
            }

        } catch (RemoteException re) {

        }
        return res;
    }

    public static boolean checkUSBExtProtocolAvailable(ModManager modManager, ModDevice modDevice) {

        boolean result = false;

        try {
            List<ModInterfaceDelegation> devices =
                    modManager.getModInterfaceDelegationsByProtocol(modDevice,
                            ModProtocol.Protocol.USB_EXT);
            result = (devices != null && !devices.isEmpty());
        } catch (RemoteException re) {
            result = false;
        }

        if (modDevice.hasDeclaredProtocol(ModProtocol.Protocol.USB_EXT)) {
            result = true;
        }

        return result;
    }


    public static boolean checkRawProtocolAvailable(ModManager modManager, ModDevice modDevice) {

        boolean result = false;

        try {
            List<ModInterfaceDelegation> devices =
                    modManager.getModInterfaceDelegationsByProtocol(modDevice,
                            ModProtocol.Protocol.RAW);
            result = (devices != null && !devices.isEmpty());
        } catch (RemoteException re) {
            result = false;
        }

        if (modDevice.hasDeclaredProtocol(ModProtocol.Protocol.RAW)) {
            result = true;
        }

        return result;
    }

}
