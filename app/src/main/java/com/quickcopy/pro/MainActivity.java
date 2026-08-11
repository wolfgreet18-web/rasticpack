package com.quickcopy.pro;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.view.*;import android.widget.*;
public class MainActivity extends Activity{
 LinearLayout list; EditText title,text;
 public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);title=findViewById(R.id.title);text=findViewById(R.id.text);list=findViewById(R.id.list);findViewById(R.id.add).setOnClickListener(v->{String t=title.getText().toString().trim(),x=text.getText().toString();if(t.isEmpty()||x.isEmpty()){Toast.makeText(this,"نام و متن را وارد کن",Toast.LENGTH_SHORT).show();return;}Store.add(this,t,x);title.setText("");text.setText("");render();});render();}
 void render(){list.removeAllViews();String[] a=Store.items(this);for(int i=0;i<a.length;i++){final int n=i;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(8,14,8,14);TextView tv=new TextView(this);tv.setText(Store.title(a[i])+"\n"+Store.text(a[i]));tv.setTextSize(16);tv.setTextColor(Color.DKGRAY);tv.setGravity(Gravity.RIGHT);row.addView(tv,new LinearLayout.LayoutParams(0,-2,1));Button del=new Button(this);del.setText("حذف");del.setOnClickListener(v->{Store.remove(this,n);render();});row.addView(del,new LinearLayout.LayoutParams(-2,-2));list.addView(row);}}
}
