package com.quickcopy.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.*;

public class MainActivity extends Activity {
  EditText input;
  @Override public void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_main);
    input=findViewById(R.id.textInput);
    Button btn=findViewById(R.id.copyButton);
    btn.setOnClickListener(v -> { String s=input.getText().toString(); if(!s.isEmpty()){ this.getSharedPreferences("quickcopy",0).edit().putString("text",s).apply(); ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE); cm.setPrimaryClip(ClipData.newPlainText("کپی سریع",s)); Toast.makeText(this,"کپی شد ✓",Toast.LENGTH_SHORT).show(); }});
  }
}
