package com.quickcopy.pro;
import android.content.*; import java.util.*;
public class Store {
 static final String PREF="quickcopy"; static final String ITEMS="items";
 static String[] items(Context c){ String s=c.getSharedPreferences(PREF,0).getString(ITEMS,""); return s.isEmpty()?new String[0]:s.split("\\u001F",-1); }
 static void add(Context c,String title,String text){ String[] a=items(c); StringBuilder b=new StringBuilder(); for(String x:a){if(!x.isEmpty()){if(b.length()>0)b.append('\u001F');b.append(x);}} if(b.length()>0)b.append('\u001F'); b.append(title.replace("\u001E"," ")).append('\u001E').append(text.replace("\u001F"," ").replace("\u001E"," ")); c.getSharedPreferences(PREF,0).edit().putString(ITEMS,b.toString()).apply(); }
 static String title(String item){int i=item.indexOf('\u001E');return i<0?item:item.substring(0,i);} static String text(String item){int i=item.indexOf('\u001E');return i<0?"":item.substring(i+1);}
 static void remove(Context c,int idx){String[] a=items(c);StringBuilder b=new StringBuilder();for(int i=0;i<a.length;i++)if(i!=idx){if(b.length()>0)b.append('\u001F');b.append(a[i]);}c.getSharedPreferences(PREF,0).edit().putString(ITEMS,b.toString()).apply();}
 static void map(Context c,int widgetId,int idx){c.getSharedPreferences(PREF,0).edit().putInt("w_"+widgetId,idx).apply();} static int map(Context c,int id){return c.getSharedPreferences(PREF,0).getInt("w_"+id,-1);}
}
