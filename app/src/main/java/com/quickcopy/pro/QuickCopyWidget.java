package com.quickcopy.pro;
import android.appwidget.*;import android.app.*;import android.content.*;import android.graphics.Color;import android.widget.*;import android.text.*;
public class QuickCopyWidget extends AppWidgetProvider{
 static final String ACTION="com.quickcopy.pro.COPY";
 public static void update(Context c,AppWidgetManager m,int id){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);int idx=Store.map(c,id);String[] a=Store.items(c);String title=idx>=0&&idx<a.length?Store.title(a[idx]):"کپی سریع";v.setTextViewText(R.id.widgetTitle,title);Intent in=new Intent(c,QuickCopyWidget.class);in.setAction(ACTION);in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);PendingIntent p=PendingIntent.getBroadcast(c,id,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);v.setOnClickPendingIntent(R.id.widgetTitle,p);m.updateAppWidget(id,v);}
 public static void update(Context c,AppWidgetManager m,int[] ids){for(int id:ids)update(c,m,id);}
 public void onUpdate(Context c,AppWidgetManager m,int[] ids){update(c,m,ids);}
 public void onReceive(Context c,Intent i){super.onReceive(c,i);if(ACTION.equals(i.getAction())){int id=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);int idx=Store.map(c,id);String[] a=Store.items(c);if(idx>=0&&idx<a.length){ClipboardManager cm=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText(Store.title(a[idx]),Store.text(a[idx])));Toast.makeText(c,"کپی شد ✓",Toast.LENGTH_SHORT).show();}}}
}
