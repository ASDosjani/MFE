package com.asdosjani.mfe;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;

import static java.lang.Thread.sleep;

public class Server extends AppCompatActivity implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private WebServer server;
    private ServerSocket serverSocket;
    Thread serverThread, mpstart = new Thread();
    private LinearLayout msgList;
    private Handler handler;
    private int greenColor;
    private ScrollView scroll;
    TextView ip, songname, currentposition, songlength, numofcondevices;
    Socket[] sockets = new Socket[30];
    String[] ips = new String[30];
    ArrayList<Integer> tempdevicenumber = new ArrayList<>();
    ListView listView;
    String[] items;
    MediaPlayer mp = new MediaPlayer();
    File filepath;
    int done, length, absposition, adapterposition;
    long offset, timeout;
    public Button playbtn, pausebtn, next, previous, startserver;
    SeekBar seekbar;
    ArrayList<File> mySongs;
    boolean changeseekbar, buttonpress, tstart, allowed, settings, servestarted;
    Switch allowclient,unplugpause;
    SearchView search;
    ArrayAdapter<String> myAdapter;
    String filter, sorting;
    public static String notificationbuttons = "";
    NotificationManagerCompat notificationManager;
    MediaSessionCompat mediaSessionCompat;
    CountDownTimer notificationcountdown = new CountDownTimer(4000, 4000) {
        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {

        }
    };

    public void goclient(View v) {
        if (notificationManager != null)
            notificationManager.cancelAll();
        startActivity(new Intent(this, Client.class));
        System.exit(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    startActivity(new Intent(this, Server.class));
                    System.exit(0);
                } else {
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
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, Server.class));
            System.exit(0);
        } else {
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
                    intent.addCategory("android.intent.category.DEFAULT");
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
        setContentView(R.layout.activity_server);
        greenColor = Color.GREEN;
        handler = new Handler();
        msgList = findViewById(R.id.msgList);
        scroll = findViewById(R.id.scroll);
        ip = findViewById(R.id.ipaddress);
        ip.setText("IP: " + getLocalIpAddress());
        listView = findViewById(R.id.listview);
        playbtn = findViewById(R.id.playbtn);
        pausebtn = findViewById(R.id.pausebtn);
        songname = findViewById(R.id.songname);
        seekbar = findViewById(R.id.seekBar);
        currentposition = findViewById(R.id.currentposition);
        songlength = findViewById(R.id.songlength);
        numofcondevices = findViewById(R.id.numofcondevices);
        allowclient = findViewById(R.id.allowclient);
        search = findViewById(R.id.search);
        next = findViewById(R.id.next);
        previous = findViewById(R.id.previous);
        startserver = findViewById(R.id.startserver);
        unplugpause= findViewById(R.id.unplugpause);
        //vheck permissions
        permission();

        mySongs = findSong(Environment.getExternalStorageDirectory());
        items = new String[mySongs.size()];

        if (mySongs.size() == 0) {
            TextView nomusic = findViewById(R.id.nomusic);
            nomusic.setVisibility(View.VISIBLE);
            return;
        }

        //restore last song
        SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
        sorting = sharedPreferences.getString("sorting", "az");


        if (sorting.equals("az")) {
            mySongs.sort(new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
                }
            });
        }
        if (sorting.equals("za")) {
            mySongs.sort(new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase()) * -1;
                }
            });
        }
        if (sorting.equals("date")) {
            mySongs.sort(new Comparator<File>() {

                @Override
                public int compare(File file1, File file2) {
                    long k = file1.lastModified() - file2.lastModified();
                    if (k > 0) {
                        return -1;
                    } else if (k == 0) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });
        }

        for (int i = 0; i < items.length; i++) {
            items[i] = mySongs.get(i).getName().replace(".mp3", "").replace(".wav", "").replace(".m4a", "");
        }
        int x = 0;
        for (; x < items.length; x++) {
            if (items[x].equals(sharedPreferences.getString("songname", ""))) {
                absposition = x;
                adapterposition = x;
                listView.setSelection(adapterposition);
                songname.setText(items[absposition]);
                break;
            }
        }
        try {
            mp.setDataSource(mySongs.get(absposition).getPath());
            mp.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        seekbar.setMax(mp.getDuration());
        mp.seekTo(sharedPreferences.getInt("currentpos", 0));
        seekbar.setProgress(sharedPreferences.getInt("currentpos", 0));
        currentposition.setText(String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(mp.getCurrentPosition()),
                TimeUnit.MILLISECONDS.toSeconds(mp.getCurrentPosition()) % 60));
        songlength.setText(String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(mp.getDuration()),
                TimeUnit.MILLISECONDS.toSeconds(mp.getDuration()) % 60));

        SharedPreferences sp = getSharedPreferences("unplug",MODE_PRIVATE);
        unplugpause.setChecked(sp.getBoolean("unplug",true));


        myAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, items) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (position == adapterposition) {
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
                } else v.setBackgroundColor(Color.TRANSPARENT);
                return v;
            }
        };
        listView.setAdapter(myAdapter);

        //Notification
        notificationManager = NotificationManagerCompat.from(this);
        mediaSessionCompat = new MediaSessionCompat(getApplicationContext(), "tag");
        NotificationChannel channel1 = new NotificationChannel(
                "channel1",
                "Music",
                NotificationManager.IMPORTANCE_HIGH + 1
        );
        channel1.setDescription("Music control");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel1);


        //headphone buttons
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1)
                .build();
        mediaSessionCompat.setPlaybackState(state);

        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSessionCompat.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (mp.isPlaying()) pause(null);
                else play(null);
                super.onPlay();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
                super.onSeekTo(pos);
            }

            @Override
            public void onPause() {
                if (mp.isPlaying()) pause(null);
                else play(null);
                super.onPause();
            }

            @Override
            public void onSkipToPrevious() {
                previous(null);
                super.onSkipToPrevious();
            }

            @Override
            public void onSkipToNext() {
                next(null);
                super.onSkipToNext();
            }
        });

        mediaSessionCompat.setActive(true);

        //headphone unplug detecting
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                    if (intent.getIntExtra("state", -1) == 0
                    && unplugpause.isChecked())
                    {
                        pause(null);
                    }
                }
            }
        };
        IntentFilter receiverFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(broadcastReceiver, receiverFilter);

        unplugpause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences sp = getSharedPreferences("unplug", MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean("unplug",isChecked);
                editor.apply();
            }
        });


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() { //new song
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                adapterposition = position;
                int x = 0;
                for (; x < items.length; x++) {
                    if (items[x].equals(myAdapter.getItem(adapterposition))) {
                        break;
                    }
                }
                absposition = x;
                newsong();
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (!buttonpress) {
                    next(null);
                }
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
                                TimeUnit.MILLISECONDS.toSeconds(progress) % 60));
                    }
                });
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                changeseekbar = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                changeseekbar = false;
                seekTo(seekBar.getProgress());
            }
        });
        allowclient.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    allowed = true;
                    for (int i = 0; i < length; i++)
                        sendMessage("allow" + String.join("/433", items), sockets[i]);
                } else {
                    allowed = false;
                    for (int i = 0; i < length; i++) sendMessage("deny", sockets[i]);
                }
            }
        });
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                myAdapter.getFilter().filter(newText);
                filter = newText;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (TextUtils.isEmpty(newText)) {
                            listView.setSelection(adapterposition);
                        }
                    }
                }, 2);
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
                while (true) { //loop
                    if (mp.isPlaying() && mp.getCurrentPosition() > 0) { //update seekbar
                        int cpos = mp.getCurrentPosition();
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
                    if (notificationbuttons != "") {
                        if (notificationbuttons.equals("previous")) previous(null);
                        else if (notificationbuttons.equals("pause"))
                            pause(null);
                        else if (notificationbuttons.equals("play"))
                            play(null);
                        else if (notificationbuttons.equals("next"))
                            next(null);
                        notificationbuttons = "";
                    }
                    if (SystemClock.elapsedRealtime() - timeout > 15000 && done != length && tstart) { //client timeout
                        tstart = false;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                showMessage(tempdevicenumber.toString(), Color.BLUE);
                                ArrayList<String> tempips = new ArrayList<>();
                                ArrayList<Socket> tempsockets = new ArrayList<>();
                                for (int i = 0; i < length; i++) {
                                    if (tempdevicenumber.contains(i)) {
                                        tempips.add(ips[i]);
                                        tempsockets.add(sockets[i]);
                                    }
                                }
                                length = tempips.size();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        numofcondevices.setText(length + " Connected devices");
                                    }
                                });
                                ips = new String[30];
                                sockets = new Socket[30];
                                for (int i = 0; i < length; i++) {
                                    ips[i] = tempips.get(i);
                                    sockets[i] = tempsockets.get(i);
                                }
                                done = 0;
                                showMessage(length + "", Color.BLUE);
                                mp.start();
                                sendOnChannel1(true);
                                offset = SystemClock.elapsedRealtime();
                                for (int i = 0; i < length; i++) sendMessage("s" + i, sockets[i]);
                                buttonpress = false;
                            }
                        }).start();
                    }
                    try {
                        sleep(25);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public ArrayList<File> findSong(File file) {
        ArrayList<File> arrayList = new ArrayList<>();
        File[] files = file.listFiles();
        if (files != null) {
            for (File singleFile : files) {
                if (singleFile.isDirectory() && !singleFile.isHidden()) {
                    arrayList.addAll(findSong(singleFile));
                } else if ((singleFile.getName().endsWith(".mp3") || singleFile.getName().endsWith(".wav")
                        || singleFile.getName().endsWith(".m4a")) && singleFile.length() > 100000) {
                    arrayList.add(singleFile);
                }
            }
        }
        return arrayList;

    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public void showmenu(View v) {
        PopupMenu sortingmenu = new PopupMenu(this, v);
        sortingmenu.setOnMenuItemClickListener(this);
        sortingmenu.inflate(R.menu.sorting_menu);
        sortingmenu.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.az: {
                mySongs.sort(new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
                    }
                });
                for (int i = 0; i < mySongs.size(); i++) {
                    items[i] = mySongs.get(i).getName().replace(".mp3", "").replace(".wav", "").replace(".m4a", "");
                }
                myAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, items) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        if (position == adapterposition) {
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
                        } else v.setBackgroundColor(Color.TRANSPARENT);
                        return v;
                    }
                };
                listView.setAdapter(myAdapter);
                myAdapter.getFilter().filter(filter);
                adapterposition = myAdapter.getPosition(songname.getText().toString());
                myAdapter.notifyDataSetChanged();

                SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                sorting = "az";
                editor.putString("songname", songname.getText().toString());
                editor.putInt("currentpos", mp.getCurrentPosition());
                editor.putString("sorting", sorting);
                editor.apply();
                return true;
            }
            case R.id.za: {
                mySongs.sort(new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase()) * -1;
                    }
                });
                for (int i = 0; i < mySongs.size(); i++) {
                    items[i] = mySongs.get(i).getName().replace(".mp3", "").replace(".wav", "").replace(".m4a", "");
                }
                myAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, items) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        if (position == adapterposition) {
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
                        } else v.setBackgroundColor(Color.TRANSPARENT);
                        return v;
                    }
                };
                listView.setAdapter(myAdapter);
                myAdapter.getFilter().filter(filter);
                adapterposition = myAdapter.getPosition(songname.getText().toString());
                myAdapter.notifyDataSetChanged();

                SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                sorting = "za";
                editor.putString("songname", songname.getText().toString());
                editor.putInt("currentpos", mp.getCurrentPosition());
                editor.putString("sorting", sorting);
                editor.apply();
                return true;
            }
            case R.id.date: {
                mySongs.sort(new Comparator<File>() {

                    @Override
                    public int compare(File file1, File file2) {
                        long k = file1.lastModified() - file2.lastModified();
                        if (k > 0) {
                            return -1;
                        } else if (k == 0) {
                            return 0;
                        } else {
                            return 1;
                        }
                    }
                });
                for (int i = 0; i < mySongs.size(); i++) {
                    items[i] = mySongs.get(i).getName().replace(".mp3", "").replace(".wav", "").replace(".m4a", "");
                }
                myAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, items) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        if (position == adapterposition) {
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
                        } else v.setBackgroundColor(Color.TRANSPARENT);
                        return v;
                    }
                };
                listView.setAdapter(myAdapter);
                myAdapter.getFilter().filter(filter);
                adapterposition = myAdapter.getPosition(songname.getText().toString());
                myAdapter.notifyDataSetChanged();

                SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                sorting = "date";
                editor.putString("songname", songname.getText().toString());
                editor.putInt("currentpos", mp.getCurrentPosition());
                editor.putString("sorting", sorting);
                editor.apply();
                return true;
            }
            default:
                return false;
        }
    }

    public void newsong() {
        try {
            //TODO file szűrés, nem névre
            //TODO játszott zene kijelzése
            //TODO értesítés sáv
            //TODO keresésben a kurzor eltüntetése
            buttonpress = true;
            mp.reset();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    myAdapter.notifyDataSetChanged();
                    playbtn.setVisibility(Button.INVISIBLE);
                    pausebtn.setVisibility(Button.VISIBLE);
                    listView.setEnabled(false);
                    songname.setText(items[absposition]);
                    seekbar.setProgress(0);
                    currentposition.setText("00:00");
                }
            });
            mp.setDataSource(mySongs.get(absposition).getPath());
            filepath = new File(mySongs.get(absposition).getPath());
            mp.prepare();
            timeout = SystemClock.elapsedRealtime();
            tempdevicenumber.clear();
            if (servestarted) {
                for (int i = 0; i < length; i++)
                    sendMessage("n" + i + "/" + mySongs.get(absposition).getName() + "/433", sockets[i]);
                tstart = true;
            } else {
                mp.start();
                buttonpress = false;
            }
            sendOnChannel1(true);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listView.setEnabled(true);
                    seekbar.setMax(mp.getDuration());
                    songlength.setText(String.format("%02d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(mp.getDuration()),
                            TimeUnit.MILLISECONDS.toSeconds(mp.getDuration()) % 60));
                }
            });

            SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("songname", songname.getText().toString());
            editor.putInt("currentpos", 0);
            editor.putString("sorting", sorting);
            editor.apply();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void next(View v) {
        adapterposition = myAdapter.getPosition(songname.getText().toString());
        if (adapterposition < myAdapter.getCount() - 1) {
            int x = 0;
            for (; x < items.length; x++) {
                if (items[x].equals(myAdapter.getItem(adapterposition + 1))) {
                    break;
                }
            }
            absposition = x;
            adapterposition++;
            buttonpress = true;
            seekbar.setProgress(0);
            mp.reset();
            timeout = SystemClock.elapsedRealtime();
            tempdevicenumber.clear();
            try {
                mp.setDataSource(mySongs.get(absposition).getPath());
                filepath = new File(mySongs.get(absposition).getPath());
                mp.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (servestarted) {
                for (int i = 0; i < length; i++)
                    sendMessage("n" + i + "/" + mySongs.get(absposition).getName() + "/433", sockets[i]);
                tstart = true;
            } else {
                mp.start();
                buttonpress = false;
            }
            seekbar.setMax(mp.getDuration());
            sendOnChannel1(true);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    myAdapter.notifyDataSetChanged();
                    playbtn.setVisibility(Button.INVISIBLE);
                    pausebtn.setVisibility(Button.VISIBLE);
                    songname.setText(items[absposition]);
                    currentposition.setText("00:00");
                    songlength.setText(String.format("%02d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(mp.getDuration()),
                            TimeUnit.MILLISECONDS.toSeconds(mp.getDuration()) % 60));
                }
            });
            SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("songname", songname.getText().toString());
            editor.putInt("currentpos", 0);
            editor.putString("sorting", sorting);
            editor.apply();
        }
    }

    public void previous(View v) {
        adapterposition = myAdapter.getPosition(songname.getText().toString());
        if (adapterposition != 0) {
            int x = 0;
            for (; x < items.length; x++) {
                if (items[x].equals(myAdapter.getItem(adapterposition - 1))) {
                    break;
                }
            }
            absposition = x;
            adapterposition--;
            buttonpress = true;
            seekbar.setProgress(0);
            mp.reset();
            timeout = SystemClock.elapsedRealtime();
            tempdevicenumber.clear();
            try {
                mp.setDataSource(mySongs.get(absposition).getPath());
                filepath = new File(mySongs.get(absposition).getPath());
                mp.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (servestarted) {
                for (int i = 0; i < length; i++)
                    sendMessage("n" + i + "/" + mySongs.get(absposition).getName() + "/433", sockets[i]);
                tstart = true;
            } else {
                mp.start();
                buttonpress = false;
            }
            seekbar.setMax(mp.getDuration());
            sendOnChannel1(true);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    myAdapter.notifyDataSetChanged();
                    playbtn.setVisibility(Button.INVISIBLE);
                    pausebtn.setVisibility(Button.VISIBLE);
                    songname.setText(items[absposition]);
                    currentposition.setText("00:00");
                    songlength.setText(String.format("%02d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(mp.getDuration()),
                            TimeUnit.MILLISECONDS.toSeconds(mp.getDuration()) % 60));
                }
            });
            SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("songname", songname.getText().toString());
            editor.putInt("currentpos", 0);
            editor.putString("sorting", sorting);
            editor.apply();
        }
    }

    public void seekTo(int pos) {
        seekbar.setProgress(pos);
        if (servestarted) {
            offset = SystemClock.elapsedRealtime();
            for (int i = 0; i < length; i++) sendMessage("st" + pos, sockets[i]);
        }
        mp.seekTo(pos, MediaPlayer.SEEK_CLOSEST);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentposition.setText(String.format("%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(mp.getCurrentPosition()),
                        TimeUnit.MILLISECONDS.toSeconds(mp.getCurrentPosition()) % 60));
            }
        });
        if (mp.isPlaying()) sendOnChannel1(true);
        else sendOnChannel1(false);
        SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("songname", songname.getText().toString());
        editor.putInt("currentpos", mp.getCurrentPosition());
        editor.putString("sorting", sorting);
        editor.apply();
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
        handler.post(new Runnable() {
                         @Override
                         public void run() {
                             msgList.addView(Server.this.textView(message, color));
                         }
                     }
        );
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(ScrollView.FOCUS_DOWN);
            }
        }, 5);
    }

    @Override
    public void onClick(View v) {
        //if (v.getId() == R.id.startserver) {


        ip.setText("IP: " + getLocalIpAddress());
        if (startserver.getText().equals("Stop server")) {
            servestarted = false;
            server.stop();
            this.serverThread.interrupt();
            scroll.setVisibility(View.INVISIBLE);
            startserver.setText("Start server");
        } else if (startserver.getText().equals("Show scroll")) {
            scroll.setVisibility(View.VISIBLE);
            startserver.setText("Stop server");
        } else if (startserver.getText().equals("Start server")) {
            servestarted = true;
            startserver.setText("Show scroll");
            msgList.removeAllViews();
            showMessage("Server Started.", Color.BLACK);
            server = new WebServer();
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.serverThread = new Thread(new Server.ServerThread());
            this.serverThread.start();
            if (getLocalIpAddress() == null) {
                Toast.makeText(this, "Wifi or hotspot required!", Toast.LENGTH_SHORT).show();
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                startActivity(panelIntent);
            }
        }
        // }
    }

    private void sendMessage(final String message, Socket s) {
        try {
            if (null != s) {
                new Thread(() -> {
                    PrintWriter out = null;
                    try {
                        out = new PrintWriter(new BufferedWriter(
                                new OutputStreamWriter(s.getOutputStream())),
                                true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    out.println(message);
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class ServerThread implements Runnable {
        Socket socket;

        @Override
        public void run() {

            try {
                serverSocket = new ServerSocket(1332);
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Starting Server : " + e.getMessage(), Color.RED);
            }
            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket = serverSocket.accept();
                        //search in previous ip
                        boolean contains = false;

                        for (int i = 0; i < 30; i++) {
                            if (socket.getInetAddress().toString().equals(ips[i])) {
                                sockets[i] = socket;
                                showMessage("same: " + i, Color.RED);
                                contains = true;
                                break;
                            }
                        }
                        if (!contains) {
                            ips[length] = socket.getInetAddress().toString();
                            sockets[length] = socket;
                            length++;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    numofcondevices.setText(length + " Connected devices");
                                }
                            });
                            showMessage(length + "", Color.BLUE);
                        }
                        showMessage(socket.toString(), Color.RED);
                        showMessage(socket.getInetAddress().toString(), Color.CYAN);
                        Server.CommunicationThread commThread = new Server.CommunicationThread(socket);
                        new Thread(commThread).start();
                        if (allowed) sendMessage("allow" + String.join("/433", items), socket);
                    } catch (IOException e) {
                        e.printStackTrace();
                        showMessage("Error Communicating to Client :" + e.getMessage(), Color.RED);
                    }
                }
            }
        }
    }

    class CommunicationThread implements Runnable {

        private BufferedReader input;

        public CommunicationThread(Socket clientSocket) {
            try {
                this.input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Connecting to Client!!", Color.RED);
            }
            showMessage("Connected to Client!!", greenColor);
        }

        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = input.readLine();
                    if (read != null) {
                        if (read.startsWith("offset")) {
                            sendMessage("offset" + SystemClock.elapsedRealtime(), sockets[Integer.parseInt(read.substring(6))]);
                        } else if (read.startsWith("of")) {
                            long o = SystemClock.elapsedRealtime() - offset;
                            sendMessage("o" + o, sockets[Integer.parseInt(read.substring(2))]);
                            showMessage("of" + o, Color.WHITE);
                        } else if (read.startsWith("sof")) {
                            long o = SystemClock.elapsedRealtime() - offset;
                            sendMessage("o" + o, sockets[Integer.parseInt(read.substring(3))]);
                            showMessage("sof" + o, Color.WHITE);
                        } else if (read.startsWith("seek")) {
                            seekTo(Integer.parseInt(read.substring(4)));
                        } else if (read.equals("p")) pause(null);
                        else if (read.equals("s")) play(null);
                        else if (read.startsWith("newsong")) {
                            int x = 0;
                            for (; x < items.length; x++) {
                                if (items[x].equals(read.substring(7))) {
                                    break;
                                }
                            }
                            absposition = x;
                            adapterposition = myAdapter.getPosition(read.substring(7));
                            newsong();
                        }//TODO
                        else if (read.startsWith("d")) {
                            tempdevicenumber.add(Integer.parseInt(read.substring(1)));
                            done++;
                        } else if (read.equals("a")) for (int i = 0; i < length; i++)
                            sendMessage("allow" + String.join("/433", items), sockets[i]);
                        else if ("Disconnect".contentEquals(read)) {
                            read = "Client Disconnected";
                            showMessage("Client : " + read, greenColor);
                            break;
                        }
                        if (done == length) {
                            tstart = false;
                            done = 0;
                            play(null);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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
        new File(getCacheDir() + "tempmusic.mp3").delete();
        if (null != serverThread) {
            for (int i = 0; i < length; i++) sendMessage("Disconnect", sockets[i]);
            serverThread.interrupt();
            serverThread = null;
        }
        if (server != null)
            server.stop();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }

    private class WebServer extends NanoHTTPD {

        public WebServer() {
            super(1331);
        }

        @Override
        public Response serve(String uri, Method method,
                              Map<String, String> header,
                              Map<String, String> parameters,
                              Map<String, String> files) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(filepath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return newChunkedResponse(NanoHTTPD.Response.Status.OK, "audio/mpeg", fis);
        }
    }

    public void play(View v) {
        buttonpress = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                playbtn.setVisibility(Button.INVISIBLE);
                pausebtn.setVisibility(Button.VISIBLE);
            }
        });
        sendOnChannel1(true);
        if (servestarted) {
            long starttime = SystemClock.elapsedRealtime() + 500;
            for (int i = 0; i < length; i++) sendMessage("s" + starttime, sockets[i]);
            mpstart.interrupt();
            mpstart = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (starttime > SystemClock.elapsedRealtime()) ;
                    mp.start();
                }
            });
            mpstart.start();
        } else mp.start();
    }

    public void pause(View v) {
        buttonpress = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pausebtn.setVisibility(View.INVISIBLE);
                playbtn.setVisibility(View.VISIBLE);
            }
        });
        sendOnChannel1(false);
        mp.pause();
        showMessage(mp.getCurrentPosition() + "", Color.GREEN);
        if (servestarted) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < length; i++)
                        sendMessage("p" + mp.getCurrentPosition(), sockets[i]);
                    mp.seekTo(mp.getCurrentPosition(), MediaPlayer.SEEK_CLOSEST);
                }
            }, 50);
        }

        SharedPreferences sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("songname", songname.getText().toString());
        editor.putInt("currentpos", mp.getCurrentPosition());
        editor.putString("sorting", sorting);
        editor.apply();
    }

    public void settings(View v) {
        final float scale = getApplicationContext().getResources().getDisplayMetrics().density;

        RelativeLayout rel = findViewById(R.id.rel);
        if (settings) {
            settings = false;
            FloatingActionButton settings = findViewById(R.id.settings);
            settings.setImageResource(R.drawable.ic_baseline_settings_24);
            View divider = findViewById(R.id.divider);
            divider.setVisibility(View.INVISIBLE);
            int pixels = (int) (10 * scale + 0.5f);
            RelativeLayout.LayoutParams rel_btn = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, pixels);
            rel.setVisibility(View.INVISIBLE);
            rel.setLayoutParams(rel_btn);
        } else {
            settings = true;
            ip.setText("IP: " + getLocalIpAddress());
            FloatingActionButton settings = findViewById(R.id.settings);
            settings.setImageResource(R.drawable.ic_baseline_close_24);
            View divider = findViewById(R.id.divider);
            divider.setVisibility(View.VISIBLE);
            int pixels = (int) (190 * scale + 0.5f);
            RelativeLayout.LayoutParams rel_btn = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, pixels);
            rel.setVisibility(View.VISIBLE);
            rel.setLayoutParams(rel_btn);
        }
    }

    public void sendOnChannel1(boolean pause) {
        notificationcountdown.cancel();
        String title = items[absposition];
        Intent activityIntent = new Intent(this, Server.class);
        activityIntent.setAction(Intent.ACTION_MAIN);
        activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent contentIntent = PendingIntent.getActivity(this,
                0, activityIntent, 0);

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
                .setState(pause ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, mp.getCurrentPosition(), 1.0f)
                .setActions(PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
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
            runOnUiThread(new Runnable() {
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
        notificationManager.notify(1, notification.build());
    }
}