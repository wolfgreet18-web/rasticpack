package com.quickcopy.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.*;
import android.text.TextUtils;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;

public class QuickCopyWidget extends AppWidgetProvider {
  private static final String PREF="quickcopy";
  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids){
    for(int id:ids){ RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_quick_copy);
      Intent i=new Intent(c,QuickCopyWidget.class).setAction("COPY").putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
      v.setOnClickPendingIntent(android.R.id.content, PendingIntentCompat.getBroadcast(c, id, i)); m.updateAppWidget(id,v); }
  }
  @Override public void onReceive(Context c, Intent i){ super.onReceive(c,i); if("COPY".equals(i.getAction())){
    String s=c.getSharedPreferences(PREF,0).getString("text","");
    if(TextUtils.isEmpty(s)){ Toast.makeText(c,"اول متن را داخل برنامه وارد کن",Toast.LENGTH_SHORT).show(); return; }
    ClipboardManager cm=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE); cm.setPrimaryClip(ClipData.newPlainText("کپی سریع",s)); Toast.makeText(c,"کپی شد ✓",Toast.LENGTH_SHORT).show();
  }}
  static class PendingIntentCompat { static android.app.PendingIntent getBroadcast(Context c,int request,Intent i){ return android.app.PendingIntent.getBroadcast(c,request,i,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE); } }
}
