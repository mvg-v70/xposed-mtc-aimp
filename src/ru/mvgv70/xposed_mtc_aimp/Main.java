package ru.mvgv70.xposed_mtc_aimp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.provider.MediaStore;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage
{
  private static Service playerService = null;
  private static String title = "";
  private static String artist = "";
  private static String album = "";
  private static String filename = "";
  private final static String TAG = "xposed-mtc-aimp";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // AppService.onCreate()
	XC_MethodHook onCreate = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Appservice.onCreate");
        playerService = (Service)param.thisObject;
        // показать версию модуля
        try 
        {
          Service app = (Service)param.thisObject; 
          Context context = app.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
        } catch (NameNotFoundException e) {}
         // обработчик com.android.music.playstatusrequest
        IntentFilter qi = new IntentFilter();
        qi.addAction("com.android.music.playstatusrequest");
        playerService.registerReceiver(tagsQueryReceiver, qi);
      }
    };
    
    // Player.initialize()
    // TODO: использовать вместо onCreate() или constructor()
   	XC_MethodHook initialize = new XC_MethodHook() {

       @Override
       protected void afterHookedMethod(MethodHookParam param) throws Throwable {
         Log.d(TAG,"Player.initialize");
         // Context fContext
      }
    };
    
    // Player.play()
   	XC_MethodHook play = new XC_MethodHook() {

       @Override
       protected void afterHookedMethod(MethodHookParam param) throws Throwable {
         Log.d(TAG,"Player.play");
         Object info = XposedHelpers.callMethod(param.thisObject, "getTrackInfo");
         title = (String)XposedHelpers.getObjectField(info, "title");
         album = (String)XposedHelpers.getObjectField(info, "album");
         artist = (String)XposedHelpers.getObjectField(info, "artist");
         filename = (String)XposedHelpers.getObjectField(info, "fileName");
         // покажем теги
         Log.d(TAG,filename);
         Log.d(TAG,"title="+title);
         Log.d(TAG,"album="+album);
         Log.d(TAG,"artist="+artist);
         // пошлем информацию о проигрываемом файле
         sendNotifyIntent(playerService);
      }
    };
    
    // Player.stop()
   	XC_MethodHook stop = new XC_MethodHook() {

       @Override
       protected void afterHookedMethod(MethodHookParam param) throws Throwable {
         Log.d(TAG,"Player.stop");
         // выключаем Receiver
         playerService.unregisterReceiver(tagsQueryReceiver);
         title = "";
         album = "";
         artist = "";
         filename = "";
         playerService = null;
      }
    };
    
    // start hooks
    if (!lpparam.packageName.equals("com.aimp.player")) return;
    Log.d(TAG,"com.aimp.player");
    XposedHelpers.findAndHookMethod("com.aimp.player.service.AppService", lpparam.classLoader, "onCreate", onCreate);
    XposedHelpers.findAndHookMethod("com.aimp.player.service.core.player.Player", lpparam.classLoader, "initialize", String.class, initialize);
    XposedHelpers.findAndHookMethod("com.aimp.player.service.core.player.Player", lpparam.classLoader, "stop", stop);
    XposedHelpers.findAndHookMethod("com.aimp.player.service.core.player.Player", lpparam.classLoader, "play", play);
    Log.d(TAG,"com.aimp.player hook OK");
  }
   
  // отправка информации о воспроизведении
  private void sendNotifyIntent(Context context)
  {
    Intent intent = new Intent("com.android.music.playstatechanged");
    intent.putExtra(MediaStore.EXTRA_MEDIA_TITLE, title);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
    intent.putExtra("filename", filename);
    intent.putExtra("source", "aimp");
    context.sendBroadcast(intent);
  }
 
  // обработчик com.android.music.querystate
  private BroadcastReceiver tagsQueryReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      // отправить информацию
      Log.d(TAG,"Aimp: tags query receiver");
      if (playerService != null)
      {
        boolean playing = (boolean)XposedHelpers.callMethod(playerService, "isPlaying");
        Log.d(TAG,"playing="+playing);
        // только в режиме проигрывания
    	if (playing) sendNotifyIntent(context);
      }
    }
  };

  
}
