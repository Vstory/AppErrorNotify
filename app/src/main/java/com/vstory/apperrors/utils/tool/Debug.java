
package com.vstory.apperrors.utils.tool;

import android.util.Log;

import com.vstory.apperrors.hook.HookEntry;


public class Debug {

    
    public static final String TAG = "AppErrorNotify";

    
    public static void d(String msg) {
        d(TAG, msg, null);
    }

    
    public static void d(String tag, String msg) {
        d(tag, msg, null);
    }

    
    public static void d(String tag, String msg, Throwable throwable) {
        
        HookEntry entry = HookEntry.getInstance();
        if (entry != null) {
            entry.log(Log.DEBUG, tag, msg != null ? msg : "", throwable);
        }
        
        if (throwable != null) {
            Log.d(tag, msg != null ? msg : "", throwable);
        } else {
            Log.d(tag, msg != null ? msg : "");
        }
    }

    private Debug() {}
}
