package br.org.eldorado.desafioford;


import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import com.motorola.mod.ModDevice;
import com.motorola.mod.ModInterfaceDelegation;
import com.motorola.mod.ModManager;
import com.motorola.mod.ModProtocol;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by wla015 on 12/09/2016.
 */
public class ModRawProtocol {

    private static final String TAG = "ModRawProtocol";

    private static final int SEND_RAW_CMD = 1;

    public final static int MSG_RAW_IO_EXCEPTION = 5;
    public final static int MSG_RAW_REQUEST_PERMISSION = 6;
    public final static int MSG_RAW_IO_READY = 7;
    public final static int MSG_RAW_DATA = 8;

    private static ModRawProtocol instance;

    private Handler handler;

    private List<Handler> listeners = new ArrayList<>();


    /**
     * Result indicator for Os.poll()
     */
    private static final int POLL_TYPE_READ_DATA = 1;
    private static final int POLL_TYPE_EXIT = 2;

    /** Output stream end indicator */
    private static final int EXIT_BYTE = 0xFF;

    /** File descriptor for RAW I/O */
    private ParcelFileDescriptor parcelFD;

    private ModDevice modDevice;
    private ModManager modManager;
    private Context context;

    /**
     * File descriptor pipes for RAW I/O. For further details,
     * refer to http://man7.org/linux/man-pages/man2/pipe.2.html
     */
    private FileDescriptor[] syncPipes;

    /** Work thread for read / write data via RAW I/O */
    private Thread receiveThread = null;
    private HandlerThread sendingThread = null;
    private FileOutputStream outputStream;


    public static boolean checkRawProtocolAvailable(ModManager modManager, ModDevice modDevice) {
        try {
            List<ModInterfaceDelegation> devices =
                    modManager.getModInterfaceDelegationsByProtocol(modDevice,
                            ModProtocol.Protocol.RAW);
            return (devices != null && !devices.isEmpty());
        } catch (RemoteException re) {
            return false;
        }
    }

    /** Constructor */
    private ModRawProtocol(Context context) {
        this.context = context;
    }

    public static ModRawProtocol getInstance(Context context) {
        if (instance == null) {
            instance = new ModRawProtocol(context);
        }
        return instance;
    }



    /** Close RAW I/O and work threads */
    private void closeRawDeviceifAvailable() {
        /** Exit write thread */
        if (null != sendingThread) {
            sendingThread.quitSafely();
        }

        /** Exit read thread */
        if (null != receiveThread) {
            signalReadThreadToExit();
        }

        /** Close the file descriptor pipes */
        if (null != syncPipes) {
            synchronized (syncPipes) {
                try {
                    Os.close(syncPipes[0]);
                    Os.close(syncPipes[1]);
                } catch (ErrnoException e) {
                    e.printStackTrace();
                }
                syncPipes = null;
            }
        }

        /** Close the file descriptor */
        if (parcelFD != null) try {
            parcelFD.close();
            parcelFD = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        sendingThread = null;
        receiveThread = null;
    }

    /** Write exit signal */
    private void signalReadThreadToExit() {
        FileOutputStream out = new FileOutputStream(syncPipes[1]);
        try {
            out.write(EXIT_BYTE);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void registerListener(Handler listener) {
        listeners.add(listener);
    }


    public void unregisterListener(Handler listener) {
        listeners.remove(listener);
    }


    protected void notifyListeners(int what) {
        for (Handler handler : listeners) {
            handler.sendEmptyMessage(what);
        }
    }

    protected void notifyListeners(Message msg) {
        for (Handler handler : listeners) {
            handler.sendMessage(msg);
        }
    }

    protected void notifyListeners(int what, int arg) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.arg1 = arg;

        for (Handler handler : listeners) {
            handler.sendMessage(msg);
        }
    }


    /**  Handle mod device attach/detach events */
    public void onModAttach(ModManager m, ModDevice d) {
        modDevice = d;
        this.modManager = m;
        openRawDeviceifAvailable();
    }

    public void onModDetach() {
        modDevice = null;
        modManager = null;
        closeRawDeviceifAvailable();
    }

    /** Clean up */
    public void onDestroy() {
        listeners.clear();
        onModDetach();
    }

    /**  Put the RAW command into event queue to execute */
    public boolean sendRawData(byte[] cmd) {
        if (null != handler) {
            Message msg = Message.obtain(handler, SEND_RAW_CMD);
            msg.obj = cmd;
            handler.sendMessage(msg);

            return true;
        } else {
            return false;
        }
    }

    /** Check RAW I/O status, open I/O streams if not yet */
    public void checkRawInterface() {
        if (null != sendingThread && null != receiveThread) {
            onRawInterfaceReady();
        } else {
            openRawDeviceifAvailable();
        }
    }

    /** Check RAW I/O status */
    public boolean isRawInterfaceReady() {
        return sendingThread != null && receiveThread != null;
    }

    /**  Write the command byte array data via RAW I/O to Moto Mod device */
    private class SendHandler extends Handler {
        public SendHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SEND_RAW_CMD:
                    try {
                        /** Write data into RAW I/O, which mod device will get */
                        if (null != outputStream) {
                            outputStream.write((byte[]) msg.obj);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "IOException while writing to raw file" + e);
                        onIOException();
                    }
                    return;
            }
            super.handleMessage(msg);
        }
    }

    /** I/O exception */
    private void onIOException() {
        notifyListeners(MSG_RAW_IO_EXCEPTION);
    }

    /** Request grant RAW_PROTOCOL permission */
    private void onRequestRawPermission() {
        notifyListeners(MSG_RAW_REQUEST_PERMISSION);
    }

    /** RAW I/O is ready to use */
    private void onRawInterfaceReady() {
        notifyListeners(MSG_RAW_IO_READY);
    }

    /** Got data from RAW I/O from mod device */
    private void onRawData(byte[] buffer, int length) {
        Message msg = Message.obtain();
        msg.what = MSG_RAW_DATA;
        msg.arg1 = length;
        msg.obj = buffer;

        notifyListeners(msg);
    }

    /** Create RAW I/O for attached mod device */
    private boolean openRawDeviceifAvailable() {
        /** Check whether mod device is available */
        if (null == modManager || null == modDevice) {
            return false;
        }

        if (context.checkSelfPermission(ModManager.PERMISSION_USE_RAW_PROTOCOL)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }


        /**
         * Check whether expecting mod attached based on PID / VID.
         * For this example we ask for MDK Blinky.
         */
//        if (targetPID != Constants.INVALID_ID && targetVID != Constants.INVALID_ID) {
//            if (modDevice.getVendorId() != targetVID || modDevice.getProductId() != targetPID) {
//                return false;
//            }
//        }

        try {
            /** Query ModManager with RAW protocol */
            List<ModInterfaceDelegation> devices =
                    modManager.getModInterfaceDelegationsByProtocol(modDevice,
                            ModProtocol.Protocol.RAW);
            if (devices != null && !devices.isEmpty()) {
                // TODO: go through the whole devices list for multi connected devices.
                // Here simply operate the first device for this example.

                ModInterfaceDelegation device = devices.get(0);
                // open RAW I/O
                getRawPfd(device);

                return true;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Get file description via ModManager for attached Moto Mod, to create RAW I/O */
    private void getRawPfd(ModInterfaceDelegation device) {
        try {
            /** Get file description of this mod device */

            if (null != sendingThread && null != receiveThread) {
                return;
            }

            parcelFD = modManager.openModInterface(device,
                    ParcelFileDescriptor.MODE_READ_WRITE);
            if (parcelFD != null) {
                try {
                    /**
                     * Get read / write file descriptor, For further details,
                     * refer to http://man7.org/linux/man-pages/man2/pipe.2.html
                     */
                    syncPipes = Os.pipe();
                } catch (ErrnoException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Log.e(TAG, "parcelFD: " + parcelFD.toString());

                /** Create work threads for read / write data */
                createSendingThread();
                createReceivingThread();

                if (null != sendingThread && null != receiveThread) {
                    /** Notify that RAW I/O is ready to use */
                    onRawInterfaceReady();
                }
            } else {
                Log.e(TAG, "getRawPfd PFD null ");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "openRawDevice exception " + e);
        }
    }

    /**
     * Create the RAW data write stream based on the file description
     * for attached mod device.
     */
    private void createSendingThread() {
        if (sendingThread == null) {
            FileDescriptor fd = parcelFD.getFileDescriptor();
            outputStream = new FileOutputStream(fd);
            sendingThread = new HandlerThread("sendingThread");
            sendingThread.start();
            handler = new SendHandler(sendingThread.getLooper());
        }
    }

    /**
     * Create the RAW data read stream based on the file description
     * for attached mod device.
     */
    public static int MAX_BYTES = 15040;

    private void createReceivingThread() {
        if (receiveThread != null) return;
        receiveThread = new Thread() {
            @Override
            public void run() {
                byte[] buffer = new byte[MAX_BYTES];
                FileDescriptor fd = parcelFD.getFileDescriptor();
                FileInputStream inputStream = new FileInputStream(fd);
                int ret = 0;
                synchronized (syncPipes) {
                    while (ret >= 0) {
                        try {
                            /** Poll on the exit pipe and the raw channel */
                            int polltype = blockRead();
                            if (polltype == POLL_TYPE_READ_DATA) {
                                ret = inputStream.read(buffer, 0, MAX_BYTES);
                                if (ret > 0) {
                                    /**  Got raw data */
                                    onRawData(buffer, ret);
                                }
                            } else if (polltype == POLL_TYPE_EXIT) {
                                break;
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "IOException while reading from raw file" + e);
                            onIOException();
                        } catch (Exception e) {
                            Log.e(TAG, "Exception while reading from raw file" + e);
                            e.printStackTrace();
                        }
                    }
                    receiveThread = null;
                }
            }
        };

        receiveThread.start();
    }

    /** Read the RAW I/O input pipe, the data is written by attached mod device */
    private int blockRead() {
        /** Poll on the pipe to see whether signal to exit, or any data on raw fd to read */
        StructPollfd[] pollfds = new StructPollfd[2];

        /** readRawFd will watch whether data is available on the raw channel */
        StructPollfd readRawFd = new StructPollfd();
        pollfds[0] = readRawFd;
        readRawFd.fd = parcelFD.getFileDescriptor();
        readRawFd.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP);

        /** syncFd will watch whether any exit signal */
        StructPollfd syncFd = new StructPollfd();
        pollfds[1] = syncFd;
        syncFd.fd = syncPipes[0];
        syncFd.events = (short) OsConstants.POLLIN;

        try {
            /** Waits for file descriptors pollfds to become ready to perform I/O */
            int ret = Os.poll(pollfds, -1);
            if (ret > 0) {
                if (syncFd.revents == OsConstants.POLLIN) {
                    /** POLLIN on the syncFd as signal to exit */
                    byte[] buffer = new byte[1];
                    new FileInputStream(syncPipes[0]).read(buffer, 0, 1);
                    return POLL_TYPE_EXIT;
                } else if ((readRawFd.revents & OsConstants.POLLHUP) != 0) {
                    /** RAW driver existing */
                    return POLL_TYPE_EXIT;
                } else if ((readRawFd.revents & OsConstants.POLLIN) != 0) {
                    /** Finally data ready to read */
                    return POLL_TYPE_READ_DATA;
                } else {
                    /** Unexcpected error */
                    Log.e(TAG, "unexpected events in blockRead rawEvents:"
                            + readRawFd.revents + " syncEvents:" + syncFd.revents);
                    return POLL_TYPE_EXIT;
                }
            } else {
                /** Error */
                Log.e(TAG, "Error in blockRead: " + ret);
            }
        } catch (ErrnoException e) {
            Log.e(TAG, "ErrnoException in blockRead: " + e);
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "IOException in blockRead: " + e);
            e.printStackTrace();
        }
        return POLL_TYPE_EXIT;
    }
}