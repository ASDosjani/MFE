package com.asdosjani.mfe;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

public class Client extends AppCompatActivity implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private ClientThread clientThread;
    private Thread thread,mpstart=new Thread();
    private LinearLayout msgList;
    private Handler handler;
    private EditText ip;
    private ScrollView scroll;
    ListView listView;
    TextView songname,currentposition,songlength;
    SeekBar seekbar;
    MediaPlayer mp = new MediaPlayer();
    String sn,type,filter;
    int devicenumber,absposition,adapterposition;
    boolean newdownload,allowed,changeseekbar,pausebutton;
    long connectoffset,clockoffset=0;
    Button playbtn,pausebtn;
    String[] items;
    ArrayAdapter<String> myAdapter;
    SearchView search;
    RelativeLayout rel;
    public static String notificationbuttons="";
    NotificationManagerCompat notificationManager;
    MediaSessionCompat mediaSessionCompat;
    CountDownTimer notificationcountdown=new CountDownTimer(4000,4000) {
        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {

        }
    };

    public void goserver(View v){
        new File("/sdcard/Download/tempmusic.mp3").delete();
        notificationManager.cancelAll();
        startActivity(new Intent(this, Server.class));
        System.exit(0);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Permission needed!", Toast.LENGTH_SHORT).show();
                    permission();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission needed!", Toast.LENGTH_SHORT).show();
            permission();
        }
    }

    public void permission() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getApplicationContext().getPackageName()));
                    startActivityForResult(intent, 100);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, 100);
                }
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //Ask for permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        handler = new Handler();
        msgList = findViewById(R.id.msgList);
        ip = findViewById(R.id.ip);
        scroll = findViewById(R.id.scroll);
        songname=findViewById(R.id.songname);
        songlength=findViewById(R.id.songlength);
        currentposition=findViewById(R.id.currentposition);
        seekbar=findViewById(R.id.seekBar);
        playbtn=findViewById(R.id.playbtn);
        pausebtn=findViewById(R.id.pausebtn);
        listView=findViewById(R.id.listview);
        search=findViewById(R.id.search);
        rel=findViewById(R.id.rel);

        permission();

        //Notification
        notificationManager = NotificationManagerCompat.from(this); //Notification
        mediaSessionCompat=new MediaSessionCompat(getApplicationContext(),"tag");
        NotificationChannel channel1 = new NotificationChannel(
                "channel1",
                "Music",
                NotificationManager.IMPORTANCE_HIGH+1
        );
        channel1.setDescription("Music control");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel1);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int x=0;
                for (;x<items.length;x++){
                    if (items[x].equals(myAdapter.getItem(position))){
                        break;
                    }
                }
                absposition=x;
                adapterposition=position;
                myAdapter.notifyDataSetChanged();
                sendOnChannel1(false);
                clientThread.sendMessage("newsong"+items[absposition]);
            }
        });

        //headphone buttons
        mediaSessionCompat.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (allowed){
                if (mp.isPlaying())pause(null);
                else play(null);}
                super.onPlay();
            }

            @Override
            public void onSeekTo(long pos) {
                if (allowed)seekTo((int)pos);
                super.onSeekTo(pos);
            }

            @Override
            public void onPause() {
                if (allowed){
                if (mp.isPlaying())pause(null);
                else play(null);}
                super.onPause();
            }
            @Override
            public void onSkipToPrevious() {
                if (allowed)previous(null);
                super.onSkipToPrevious();
            }

            @Override
            public void onSkipToNext() {
                if (allowed)next(null);
                super.onSkipToNext();
            }
        });

        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT|PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1)
                .build();
        mediaSessionCompat.setPlaybackState(state);

        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSessionCompat.setActive(true);


        seekbar.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (allowed)return false;
                else return true;
            }
        });
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentposition.setText(String.format("%02d:%02d",
                                TimeUnit.MILLISECONDS.toMinutes(progress),
                                TimeUnit.MILLISECONDS.toSeconds(progress) % 60 ));
                    }
                });
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                changeseekbar=true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekTo(seekbar.getProgress());
            }
        });
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (myAdapter!=null)
                myAdapter.getFilter().filter(newText);
                filter=newText;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (TextUtils.isEmpty(newText)){
                            listView.setSelection(adapterposition);
                        }
                    }
                },2);
                return false;
            }
        });
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search.setIconified(false);
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    if (mp.isPlaying()) {
                        int cpos=mp.getCurrentPosition();
                        if (!changeseekbar) {
                            seekbar.setProgress(cpos);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    currentposition.setText(String.format("%02d:%02d",
                                            TimeUnit.MILLISECONDS.toMinutes(cpos),
                                            TimeUnit.MILLISECONDS.toSeconds(cpos) % 60));
                                }
                            });
                        }
                    }
                    try {
                        sleep(25);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (notificationbuttons!=""){
                        if (notificationbuttons.equals("previous"))previous(null);
                        else if(notificationbuttons.equals("pause"))
                            pause(null);
                        else if(notificationbuttons.equals("play"))
                            play(null);
                        else if(notificationbuttons.equals("next"))
                            next(null);
                        notificationbuttons="";
                    }
                }}
        }).start();
    }
    public void showmenu(View v){
        PopupMenu sortingmenu = new PopupMenu(this,v);
        sortingmenu.setOnMenuItemClickListener(this);
        sortingmenu.inflate(R.menu.client_sorting_menu);
        sortingmenu.show();
    }
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (items!=null) {
            switch (item.getItemId()) {
                case R.id.az:
                    Arrays.asList(items).sort(new Comparator<String>() {
                        @Override
                        public int compare(String o1, String o2) {
                            return o1.toLowerCase().compareTo(o2.toLowerCase());
                        }
                    });
                    for (int i = 0; i < items.length; i++) {
                        items[i] = items[i].replace(".mp3", "").replace(".wav", "").replace(".m4a", "");
                    }
                    myAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, items){
                        @NonNull
                        @Override
                        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                            View v =super.getView(position, convertView, parent);
                            if (position==adapterposition){
                                int nightModeFlags =
                                        getContext().getResources().getConfiguration().uiMode &
                                                Configuration.UI_MODE_NIGHT_MASK;
                                switch (nightModeFlags) {
                                    case Configuration.UI_MODE_NIGHT_YES:
                                        v.setBackgroundColor(Color.DKGRAY);
                                        break;

                                    case Configuration.UI_MODE_NIGHT_NO:
                                        v.setBackgroundColor(Color.LTGRAY);
                                        break;

                                    case Configuration.UI_MODE_NIGHT_UNDEFINED:
                                        v.setBackgroundColor(Color.LTGRAY);
                                        break;
                                }
                            }
                            else v.setBackgroundColor(Color.TRANSPARENT);
                            return v;
                        }
                    };
                    listView.setAdapter(myAdapter);
                    myAdapter.getFilter().filter(filter);
                    adapterposition=myAdapter.getPosition(songname.getText().toString());
                    myAdapter.notifyDataSetChanged();
                    return true;
                case R.id.za:
                    Arrays.asList(items).sort(new Comparator<String>() {
                        @Override
                        public int compare(String o1, String o2) {
                            return o1.toLowerCase().compareTo(o2.toLowerCase()) * -1;
                        }
                    });
                    for (int i = 0; i < items.length; i++) {
                        items[i] = items[i].replace(".mp3", "").replace(".wav", "").replace(".m4a", "");
                    }
                    myAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, items){
                        @NonNull
                        @Override
                        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                            View v =super.getView(position, convertView, parent);
                            if (position==adapterposition){
                                int nightModeFlags =
                                        getContext().getResources().getConfiguration().uiMode &
                                                Configuration.UI_MODE_NIGHT_MASK;
                                switch (nightModeFlags) {
                                    case Configuration.UI_MODE_NIGHT_YES:
                                        v.setBackgroundColor(Color.DKGRAY);
                                        break;

                                    case Configuration.UI_MODE_NIGHT_NO:
                                        v.setBackgroundColor(Color.LTGRAY);
                                        break;

                                    case Configuration.UI_MODE_NIGHT_UNDEFINED:
                                        v.setBackgroundColor(Color.LTGRAY);
                                        break;
                                }
                            }
                            else v.setBackgroundColor(Color.TRANSPARENT);
                            return v;
                        }
                    };
                    listView.setAdapter(myAdapter);
                    myAdapter.getFilter().filter(filter);
                    adapterposition=myAdapter.getPosition(songname.getText().toString());
                    myAdapter.notifyDataSetChanged();
                    return true;
                default:
                    return false;
            }
        }
        else return false;
    }
    public TextView textView(String message, int color) {
        if (null == message || message.trim().isEmpty()) {
            message = "<Empty Message>";
        }
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setText(message + " [" + getTime() + "]");
        tv.setTextSize(20);
        tv.setPadding(0, 5, 0, 0);
        return tv;
    }

    public void showMessage(final String message, final int color) {
        handler.post(() -> {
            msgList.addView(textView(message, color));
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(ScrollView.FOCUS_DOWN);
            }
        },5);
    }

    @Override
    public void onClick(View view) {
        msgList.removeAllViews();
        listView.removeAllViewsInLayout();
        listView.setAdapter(null);
        items=null;
        sn=null;
        showMessage("Connecting to Server...", Color.GREEN);
        ConnectivityManager conMan = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conMan.getActiveNetworkInfo();
        if (netInfo == null){
            Toast.makeText(this,"Wifi required!",Toast.LENGTH_SHORT).show();
            Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
            startActivity(panelIntent);
        }
        clientThread = new ClientThread();
        if(thread!=null) thread.interrupt();
        thread = new Thread(clientThread);
        thread.start();
        showMessage("Connected to Server...", Color.GREEN);
    }

    class ClientThread implements Runnable {

        private Socket socket;
        private BufferedReader input;

        @Override
        public void run() {

            try {
                socket = new Socket(ip.getText().toString(), 1332);

                while (!Thread.currentThread().isInterrupted()) {

                    this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String message = input.readLine();

                    if (message != null) {

                        if (message.startsWith("offset")){
                        connectoffset=SystemClock.elapsedRealtime()-connectoffset;
                        clockoffset=SystemClock.elapsedRealtime()-Long.parseLong(message.substring(6));
                            if (clockoffset>0) {
                               clockoffset+=(connectoffset+10);
                            }
                        else clockoffset-=(connectoffset+10);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(),"Connect offset: " + connectoffset+"\nClock offset: " + clockoffset, Toast.LENGTH_SHORT).show();
                            }
                        });
                        }
                        else if (message.startsWith("o")) {//offset
                            mp.seekTo(Integer.parseInt(message.substring(1))+10+mp.getCurrentPosition(),MediaPlayer.SEEK_CLOSEST);
                        //TODO asd
                        } else if (message.startsWith("st")) {//seekto
                            mp.seekTo(Integer.parseInt(message.substring(2)),MediaPlayer.SEEK_CLOSEST);
                            if (mp.isPlaying())sendMessage("sof" + devicenumber);
                            seekbar.setProgress(Integer.parseInt(message.substring(2)));
                            changeseekbar = false;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    currentposition.setText(String.format("%02d:%02d",
                                            TimeUnit.MILLISECONDS.toMinutes(mp.getCurrentPosition()),
                                            TimeUnit.MILLISECONDS.toSeconds(mp.getCurrentPosition()) % 60 ));
                                }
                            });
                            if (mp.isPlaying())sendOnChannel1(true); else sendOnChannel1(false);
                        } else if (message.startsWith("s")) {//start
                            /*if (message.length() > 1)
                                devicenumber = Integer.parseInt(message.substring(1));
                            sendMessage("of" + devicenumber);*/

                            if (allowed){
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        playbtn.setVisibility(View.INVISIBLE);
                                        pausebtn.setVisibility(View.VISIBLE);
                                    }
                                });
                            }
                            long starttime = Long.parseLong(message.substring(1))+clockoffset;
                            mpstart.interrupt();
                            mpstart = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    while (starttime>SystemClock.elapsedRealtime());
                                    mp.start();
                                }
                            });
                            mpstart.start();
                            sendOnChannel1(true);
                        } else if (message.startsWith("n")) {
                            mp.reset();
                            devicenumber = Integer.parseInt(message.substring(1, message.indexOf("/")));
                            type = message.substring(message.indexOf("/433") - 4, message.indexOf("/433"));
                            sn = message.substring(message.indexOf("/") + 1, message.indexOf("/433")).replace(".mp3", "").replace(".wav", "").replace(".m4a", "");
                            int x=0;
                            if(items!=null){
                                adapterposition=myAdapter.getPosition(sn);
                                for (;x<items.length;x++){
                                    if (items[x].equals(sn)){
                                        break;
                                    }
                                }
                            }
                            absposition=x;
                            sendOnChannel1(false);
                            newdownload = true;

                            /////////////////
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    connectoffset=SystemClock.elapsedRealtime();
                                    sendMessage("offset"+devicenumber);
                                    download();
                                }
                            },devicenumber*10);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (allowed)myAdapter.notifyDataSetChanged();
                                    songname.setText(sn);
                                    seekbar.setProgress(0);
                                    currentposition.setText("00:00");
                                }
                            });
                        } else if (message.startsWith("p")) {
                            mp.pause();
                            int a = Integer.parseInt(message.substring(1));
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mp.seekTo(a,MediaPlayer.SEEK_CLOSEST);
                                }
                            },50);
                            /*showMessage(mp.getCurrentPosition() + "", Color.GREEN);
                            showMessage(message,Color.RED);*/
                            if (allowed) {
                                pausebutton=true;
                                sendOnChannel1(false);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        playbtn.setVisibility(View.VISIBLE);
                                        pausebtn.setVisibility(View.INVISIBLE);
                                    }
                                });
                            }
                        }
                        else if (message.startsWith("allow")) {
                            allowed = true;
                            if (items==null) {
                                items = message.substring(5).split("/433");
                                Arrays.asList(items).sort(new Comparator<String>() {
                                    @Override
                                    public int compare(String o1, String o2) {
                                        return o1.toLowerCase().compareTo(o2.toLowerCase());
                                    }
                                });
                                myAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, items){
                                    @NonNull
                                    @Override
                                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                        View v =super.getView(position, convertView, parent);
                                        if (position==adapterposition){
                                            int nightModeFlags =
                                                    getContext().getResources().getConfiguration().uiMode &
                                                            Configuration.UI_MODE_NIGHT_MASK;
                                            switch (nightModeFlags) {
                                                case Configuration.UI_MODE_NIGHT_YES:
                                                    v.setBackgroundColor(Color.DKGRAY);
                                                    break;

                                                case Configuration.UI_MODE_NIGHT_NO:
                                                    v.setBackgroundColor(Color.LTGRAY);
                                                    break;

                                                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                                                    v.setBackgroundColor(Color.LTGRAY);
                                                    break;
                                            }
                                        }
                                        else v.setBackgroundColor(Color.TRANSPARENT);
                                        return v;
                                    }
                                };
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        myAdapter.getFilter().filter(search.getQuery());
                                        listView.setAdapter(myAdapter);
                                    }
                                });
                            }
                            else {
                                int x=0;
                                for (;x<items.length;x++){
                                    if (items[x].equals(sn)){
                                        break;
                                    }
                                }
                                absposition=x;
                                if(mp.isPlaying())sendOnChannel1(true); else sendOnChannel1(false);
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mp.isPlaying()) {
                                        pausebtn.setVisibility(View.VISIBLE);
                                        playbtn.setVisibility(View.INVISIBLE);
                                    } else {
                                        pausebtn.setVisibility(View.INVISIBLE);
                                        playbtn.setVisibility(View.VISIBLE);
                                    }
                                    rel.setVisibility(View.VISIBLE);
                                    Button next, previous;
                                    next = findViewById(R.id.next);
                                    previous = findViewById(R.id.previous);
                                    next.setVisibility(View.VISIBLE);
                                    previous.setVisibility(View.VISIBLE);
                                }
                            });
                        } else if (message.equals("deny")) {
                            allowed = false;
                            notificationManager.cancelAll();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    playbtn.setVisibility(View.INVISIBLE);
                                    pausebtn.setVisibility(View.INVISIBLE);
                                    rel.setVisibility(View.INVISIBLE);
                                    Button next, previous;
                                    next = findViewById(R.id.next);
                                    previous = findViewById(R.id.previous);
                                    next.setVisibility(View.INVISIBLE);
                                    previous.setVisibility(View.INVISIBLE);
                                }
                            });
                        } else if (null == message || "Disconnect".contentEquals(message)) {
                            message = "Server Disconnected.";
                            showMessage(message, Color.RED);
                            break;
                        }
                        showMessage("Server: " + message, Color.GREEN);
                    }
                }
            } catch(UnknownHostException e1){
                e1.printStackTrace();
            } catch(IOException e1){
                e1.printStackTrace();
            }

        }

        void sendMessage(final String message) {
            new Thread(() -> {
                try {
                    if (null != socket) {
                        PrintWriter out = new PrintWriter(new BufferedWriter(
                                new OutputStreamWriter(socket.getOutputStream())),
                                true);
                        out.println(message);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

    }

    String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        return sdf.format(new Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        notificationManager.cancelAll();
        new File(getCacheDir()+"tempmusic.mp3").delete();
        if (null != clientThread) {
            clientThread.sendMessage("Disconnect");
            clientThread = null;
        }
    }
    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }
    public void download() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                new File(getCacheDir()+"tempmusic.mp3").delete();
                File file = new File(getCacheDir()+"tempmusic.mp3");
                file.deleteOnExit();
                try (BufferedInputStream inputStream = new BufferedInputStream(new URL("http://"+ip.getText()+":1331").openStream());
                     FileOutputStream fileOS = new FileOutputStream(file)) {
                    byte data[] = new byte[1024];
                    int byteContent;
                    newdownload=false;
                    while ((byteContent = inputStream.read(data, 0, 1024)) != -1) {
                        if (newdownload) {file.delete(); Thread.currentThread().interrupt(); break;}
                        fileOS.write(data, 0, byteContent);
                    }
                    if (file.length()>100000&&!newdownload){
                        mp.setDataSource(getCacheDir()+"tempmusic.mp3");
                        mp.prepare();
                        seekbar.setMax(mp.getDuration());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                songlength.setText(String.format("%02d:%02d",
                                        TimeUnit.MILLISECONDS.toMinutes(mp.getDuration()),
                                        TimeUnit.MILLISECONDS.toSeconds(mp.getDuration()) % 60 ));
                            }
                        });
                        clientThread.sendMessage("d"+devicenumber);
                    }
                } catch (IOException e) {
                    // handles IO exceptions
                }
            }
        }).start();
    }
    public void keep(View v){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Files.copy(new File(getCacheDir()+"tempmusic.mp3").toPath(),
                            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/" + sn + type).toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"Music saved",Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
    public void play(View v) {
        if (!mp.isPlaying()){
            clientThread.sendMessage("s");
            sendOnChannel1(true);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playbtn.setVisibility(View.INVISIBLE);
                    pausebtn.setVisibility(View.VISIBLE);
                }
            });
        }
    }
    public void pause(View v){
        if (mp.isPlaying()){
            clientThread.sendMessage("p");
            pausebutton=true;
            sendOnChannel1(false);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playbtn.setVisibility(View.VISIBLE);
                    pausebtn.setVisibility(View.INVISIBLE);
                }
            });
        }
    }
    public void next(View v) {
        adapterposition=myAdapter.getPosition(songname.getText().toString());
        if (adapterposition <myAdapter.getCount()-1){
            int x=0;
            for (;x<items.length;x++){
                if (items[x].equals(myAdapter.getItem(adapterposition + 1))){
                    break;
                }
            }
            absposition=x;
            sendOnChannel1(false);
            clientThread.sendMessage("newsong"+items[absposition]);
        }
    }
    public void previous(View v) {
        adapterposition=myAdapter.getPosition(songname.getText().toString());
        if (adapterposition !=0) {
            int x = 0;
            for (; x < items.length; x++) {
                if (items[x].equals(myAdapter.getItem(adapterposition - 1))) {
                    break;
                }
            }
            absposition = x;
            sendOnChannel1(false);
            clientThread.sendMessage("newsong"+items[absposition]);
        }
    }
    public void seekTo(int pos) {
        mp.seekTo(pos,MediaPlayer.SEEK_CLOSEST);
        seekbar.setProgress(pos);
        clientThread.sendMessage("seek"+pos);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentposition.setText(String.format("%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(pos),
                        TimeUnit.MILLISECONDS.toSeconds(pos) % 60 ));
            }
        });
        if (mp.isPlaying())sendOnChannel1(true);else sendOnChannel1(false);
    }
    public void sendOnChannel1(boolean pause) {
        if (sn != null&&items!=null) {
            notificationcountdown.cancel();
            String title = items[absposition];
            Intent activityIntent = new Intent(this, Client.class);
            activityIntent.setAction(Intent.ACTION_MAIN);
            activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent contentIntent = PendingIntent.getActivity(this,
                    12, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent broadcastprevious = new Intent(this, NotificationReceiver.class);
            broadcastprevious.putExtra("toastMessage", "previous");
            PendingIntent actionprevious = PendingIntent.getBroadcast(this,
                    0, broadcastprevious, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent broadcastpause = new Intent(this, NotificationReceiver.class);
            broadcastpause.putExtra("toastMessage", "pause");
            PendingIntent actionpause = PendingIntent.getBroadcast(this,
                    1, broadcastpause, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent broadcastplay = new Intent(this, NotificationReceiver.class);
            broadcastplay.putExtra("toastMessage", "play");
            PendingIntent actionplay = PendingIntent.getBroadcast(this,
                    2, broadcastplay, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent broadcastnext = new Intent(this, NotificationReceiver.class);
            broadcastnext.putExtra("toastMessage", "next");
            PendingIntent actionnext = PendingIntent.getBroadcast(this,
                    3, broadcastnext, PendingIntent.FLAG_UPDATE_CURRENT);

            PlaybackStateCompat mStateBuilder = new PlaybackStateCompat.Builder()
                    .setState(pause ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, mp.getDuration()>mp.getCurrentPosition()?mp.getCurrentPosition():0, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT|PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                    .build();

            mediaSessionCompat.setMetadata(
                    new MediaMetadataCompat.Builder()
                            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mp.getDuration())
                            .build()
            );
            mediaSessionCompat.setPlaybackState(mStateBuilder);

            Bitmap l = BitmapFactory.decodeResource(getResources(), R.drawable.logo);
            NotificationCompat.Builder notification;
            if (pause) {
                pausebutton=false;
                notification = new NotificationCompat.Builder(this, "channel1")
                        .setSmallIcon(R.drawable.logo)
                        .setLargeIcon(l)
                        .setContentTitle(title)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setContentIntent(contentIntent)
                        .setOnlyAlertOnce(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_baseline_skip_previous_24, "Previous", actionprevious)
                        .addAction(R.drawable.ic_baseline_pause_24, "Pause", actionpause)
                        .addAction(R.drawable.ic_baseline_skip_next_24, "Next", actionnext)
                        .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                                .setShowActionsInCompactView(0, 1, 2)
                                .setMediaSession(mediaSessionCompat.getSessionToken()));
            } else {
                notification = new NotificationCompat.Builder(this, "channel1")
                        .setSmallIcon(R.drawable.logo)
                        .setLargeIcon(l)
                        .setContentTitle(title)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setContentIntent(contentIntent)
                        .setOnlyAlertOnce(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_baseline_skip_previous_24, "Previous", actionprevious)
                        .addAction(R.drawable.ic_baseline_play_arrow_24, "Play", actionplay)
                        .addAction(R.drawable.ic_baseline_skip_next_24, "Next", actionnext)
                        .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                                .setShowActionsInCompactView(0, 1, 2)
                                .setMediaSession(mediaSessionCompat.getSessionToken()));
                if (pausebutton){runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notificationcountdown = new CountDownTimer(4000, 4000) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                            }

                            @Override
                            public void onFinish() {
                                NotificationCompat.Builder notification1 = new NotificationCompat.Builder(getApplicationContext(), "channel1")
                                        .setSmallIcon(R.drawable.logo)
                                        .setLargeIcon(l)
                                        .setContentTitle(title)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                                        .setContentIntent(contentIntent)
                                        .setOnlyAlertOnce(true)
                                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                        .addAction(R.drawable.ic_baseline_skip_previous_24, "Previous", actionprevious)
                                        .addAction(R.drawable.ic_baseline_play_arrow_24, "Play", actionplay)
                                        .addAction(R.drawable.ic_baseline_skip_next_24, "Next", actionnext)
                                        .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                                                .setShowActionsInCompactView(0, 1, 2)
                                                .setMediaSession(mediaSessionCompat.getSessionToken()));
                                notificationManager.notify(1, notification1.build());
                            }
                        }.start();
                    }
                });
            }
            }
            notificationManager.notify(1, notification.build());
        }
    }
}