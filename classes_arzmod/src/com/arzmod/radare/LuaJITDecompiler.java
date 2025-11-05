package com.arzmod.radare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.text.method.ScrollingMovementMethod;
import android.text.method.LinkMovementMethod;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LuaJITDecompiler {
    private static final String TAG = "LuaJITDecompiler";
    
    static {
        try {
            System.loadLibrary("luajit-decompiler");
            Log.d(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library", e);
        }
    }
    
    public interface ProgressCallback {
        void onProgress(String message);
    }
    
    public interface ErrorCallback {
        void onError(String message);
    }
    
    public interface ProgressUpdateCallback {
        void onProgressUpdate(int percentage, int current, int total);
    }
    
    private ProgressCallback progressCallback;
    private ErrorCallback errorCallback;
    private ProgressUpdateCallback progressUpdateCallback;
    
    private static AlertDialog progressDialog;
    private static TextView progressText;
    private static TextView logText;
    private static ProgressBar progressBar;
    private static ScrollView logScrollView;
    private static StringBuilder logBuilder;
    private static Handler mainHandler;
    private static AtomicInteger currentProgress;
    
    public static class DecompilerOptions {
        public boolean forceOverwrite = false;
        public boolean ignoreDebugInfo = false;
        public boolean minimizeDiffs = false;
        public boolean unrestrictedAscii = true;
        public boolean silentAssertions = false;
        public String extensionFilter = "";
    }
    
    public LuaJITDecompiler() {
        setCallbacks(null, null, null);
        
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        if (logBuilder == null) {
            logBuilder = new StringBuilder();
        }
        if (currentProgress == null) {
            currentProgress = new AtomicInteger(0);
        }
    }
    
    
    public void setCallbacks(ProgressCallback progress, ErrorCallback error, ProgressUpdateCallback progressUpdate) {
        this.progressCallback = progress;
        this.errorCallback = error;
        this.progressUpdateCallback = progressUpdate;
        
        setNativeCallbacks(progress, error, progressUpdate);
    }
    

    
    public static void showDecompiler(boolean forceOverwrite, boolean unrestrictedAscii) {
        Activity activity = AppContext.getActivity();
        if (activity == null) {
            Log.e(TAG, "Activity is null, cannot show decompiler dialog");
            return;
        }
        
        LuaJITDecompiler decompiler = new LuaJITDecompiler();
        DecompilerOptions options = new DecompilerOptions();
        
        options.forceOverwrite = forceOverwrite;
        options.unrestrictedAscii = unrestrictedAscii;
        
        decompiler.showFileSelectionDialog(activity, options);
    }
    
    private void showCreditsDialog(Activity activity) {
        SpannableString creditsText = new SpannableString(
            "Используется:\n\n" +
            "LuaJIT Decompiler v2\n" +
            "Автор: marsinator358\n" +
            "GitHub: github.com/marsinator358/luajit-decompiler-v2\n\n" +
            "Unprot модуль\n" +
            "Автор: sᴀxᴏɴ\n" +
            "Форум: www.blast.hk/threads/185217/"
        );
        
        ClickableSpan decompilerLink = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Intent intent = new Intent(Intent.ACTION_VIEW, 
                    Uri.parse("https://github.com/marsinator358/luajit-decompiler-v2"));
                activity.startActivity(intent);
            }
        };
        
        ClickableSpan unprotLink = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Intent intent = new Intent(Intent.ACTION_VIEW, 
                    Uri.parse("https://www.blast.hk/threads/185217/"));
                activity.startActivity(intent);
            }
        };
        
        int decompilerStart = creditsText.toString().indexOf("github.com");
        int decompilerEnd = decompilerStart + "github.com/marsinator358/luajit-decompiler-v2".length();
        creditsText.setSpan(decompilerLink, decompilerStart, decompilerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        creditsText.setSpan(new ForegroundColorSpan(Color.parseColor("#FF4081")), 
            decompilerStart, decompilerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        int unprotStart = creditsText.toString().indexOf("www.blast.hk");
        int unprotEnd = unprotStart + "www.blast.hk/threads/185217/".length();
        creditsText.setSpan(unprotLink, unprotStart, unprotEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        creditsText.setSpan(new ForegroundColorSpan(Color.parseColor("#FF4081")), 
            unprotStart, unprotEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        TextView messageView = new TextView(activity);
        messageView.setText(creditsText);
        messageView.setTextColor(Color.WHITE);
        messageView.setTextSize(14);
        messageView.setPadding(50, 50, 50, 50);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        messageView.setLinkTextColor(Color.parseColor("#FF4081"));
        
        new AlertDialog.Builder(activity)
            .setTitle("О декомпиляторе")
            .setView(messageView)
            .setPositiveButton("OK", null)
            .show();
    }
    
    private View createTitleView(Activity activity, String title) {
        LinearLayout titleLayout = new LinearLayout(activity);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setPadding(40, 40, 40, 20);
        
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleView.setLayoutParams(titleParams);
        titleLayout.addView(titleView);
        
        ImageButton infoButton = new ImageButton(activity);
        infoButton.setImageResource(android.R.drawable.ic_menu_info_details);
        infoButton.setBackgroundColor(Color.TRANSPARENT);
        infoButton.setColorFilter(Color.WHITE);
        infoButton.setPadding(4, 4, 4, 4);
        infoButton.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreditsDialog(activity);
            }
        });
        float density = activity.getResources().getDisplayMetrics().density;
        int iconSize = (int)(24 * density);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        buttonParams.setMargins(10, 0, 0, 0);
        buttonParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        infoButton.setLayoutParams(buttonParams);
        titleLayout.addView(infoButton);
        
        return titleLayout;
    }
    
    public void showFileSelectionDialog(Activity activity, DecompilerOptions options) {
        String defaultPath = Environment.getExternalStorageDirectory() + "/Android/media/" + 
                           activity.getPackageName() + "/monetloader";
        
        File defaultDir = new File(defaultPath);
        if (!defaultDir.exists()) {
            defaultDir = Environment.getExternalStorageDirectory();
        }
        
        File[] files = defaultDir.listFiles();
        if (files == null || files.length == 0) {
            Toast.makeText(activity, "Файлы не найдены в " + defaultPath, Toast.LENGTH_LONG).show();
            return;
        }
        
        List<File> luaFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) {
                try {
                    if (isFileHaveBC(file.getAbsolutePath())) {
                        luaFiles.add(file);
                    }
                } catch (Throwable t) {
                }
            }
        }
        
        if (luaFiles.isEmpty()) {
            Toast.makeText(activity, "LuaJIT-файлы не найдены", Toast.LENGTH_LONG).show();
            return;
        }
        
        String[] fileNames = new String[luaFiles.size()];
        for (int i = 0; i < luaFiles.size(); i++) {
            fileNames[i] = luaFiles.get(i).getName();
        }
        
        boolean[] selectedFiles = new boolean[fileNames.length];
        
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setCustomTitle(createTitleView(activity, "Выберите файл для декомпиляции"))
            .setMultiChoiceItems(fileNames, selectedFiles, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    selectedFiles[which] = isChecked;
                }
            })
            .setPositiveButton("Decompile", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    List<File> selected = new ArrayList<>();
                    for (int i = 0; i < selectedFiles.length; i++) {
                        if (selectedFiles[i]) {
                            selected.add(luaFiles.get(i));
                        }
                    }
                    if (!selected.isEmpty()) {
                        showOptionsDialog(activity, selected, options);
                    } else {
                        Toast.makeText(activity, "Пожалуйста, выберите хотя бы один файл", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .create();
        dialog.show();

        Toast.makeText(activity, "Если вы не знаете что это, закройте и не пользуйтесь.", Toast.LENGTH_LONG).show();
    }
    
    private void showOptionsDialog(Activity activity, List<File> selectedFiles, DecompilerOptions options) { 
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCustomTitle(createTitleView(activity, "Настройки декомпиляции"));
        
        String[] optionNames = {
            "Удалить исходный файл .luac",
            "Ignore Debug Info", 
            "Minimize Diffs",
            "Поддержка ASCII",
            "Silent Assertions"
        };
        
        boolean[] optionValues = {
            options.forceOverwrite,
            options.ignoreDebugInfo,
            options.minimizeDiffs,
            options.unrestrictedAscii,
            options.silentAssertions
        };
        
        builder.setMultiChoiceItems(optionNames, optionValues, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                optionValues[which] = isChecked;
            }
        });
        
        builder.setPositiveButton("Запуск", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                options.forceOverwrite = optionValues[0];
                options.ignoreDebugInfo = optionValues[1];
                options.minimizeDiffs = optionValues[2];
                options.unrestrictedAscii = optionValues[3];
                options.silentAssertions = optionValues[4];
                
                startDecompilation(activity, selectedFiles, options);
            }
        });
        
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
    
    private void createEnhancedProgressDialog(Activity activity, int totalFiles) {
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);
        
        progressText = new TextView(activity);
        progressText.setText("Starting decompilation...");
        progressText.setTextColor(Color.WHITE);
        progressText.setTextSize(16);
        progressText.setPadding(0, 0, 0, 20);
        
        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setPadding(0, 0, 0, 20);
    
        logText = new TextView(activity);
        logText.setText("Logs will appear here...");
        logText.setTextColor(Color.LTGRAY);
        logText.setTextSize(12);
        logText.setMovementMethod(new ScrollingMovementMethod());
        logText.setMaxLines(10);
        logText.setVerticalScrollBarEnabled(true);
        
        logScrollView = new ScrollView(activity);
        logScrollView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            300
        ));
        logScrollView.addView(logText);
        
        mainLayout.addView(progressText);
        mainLayout.addView(progressBar);
        mainLayout.addView(logScrollView);
        
        progressDialog = new AlertDialog.Builder(activity)
            .setCustomTitle(createTitleView(activity, "LuaJIT Decompiler - Progress"))
            .setView(mainLayout)
            .setCancelable(false)
            .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    progressDialog.dismiss();
                    cleanup();
                }
            })
            .create();
        
        progressDialog.show();
    }
    
    private void addLog(String message) {
        if (logBuilder != null) {
            logBuilder.append(message).append("\n");
        }
        
        if (logText != null && mainHandler != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    logText.setText(logBuilder.toString());
                    if (logScrollView != null) {
                        logScrollView.post(new Runnable() {
                            @Override
                            public void run() {
                                logScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                }
            });
        }
    }
    
    private void updateProgress(int progress, String message) {
        if (progressBar != null && progressText != null && mainHandler != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setProgress(progress);
                    progressText.setText(message);
                }
            });
        }
    }
    
    private void updateProgress(int percentage, int current, int total) {
        if (progressBar != null && progressText != null && mainHandler != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setProgress(percentage);
                    progressText.setText("Decompiling: " + current + "/" + total + " (" + percentage + "%)");
                }
            });
        }
    }
    
    private void updateProgressDirect(int percentage, int current, int total) {
        if (progressBar != null && progressText != null && mainHandler != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setProgress(percentage);
                    progressText.setText("Decompiling: " + current + "/" + total + " (" + percentage + "%)");
                }
            });
        }
    }
    
    private void startDecompilation(Activity activity, List<File> selectedFiles, 
                                   DecompilerOptions options) {
        logBuilder.setLength(0);
        currentProgress.set(0);
        
        createEnhancedProgressDialog(activity, selectedFiles.size());
        
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addLog("Starting decompilation of " + selectedFiles.size() + " files...");
            }
        });
        
        setCallbacks(
            new ProgressCallback() {
                @Override
                public void onProgress(String message) {
                    addLog(message);
                }
            },
            new ErrorCallback() {
                @Override
                public void onError(String message) {
                    addLog("ERROR: " + message);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(activity)
                                .setTitle("Decompilation error")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                        }
                    });
                }
            },
            new ProgressUpdateCallback() {
                @Override
                public void onProgressUpdate(int percentage, int current, int total) {
                    updateProgressDirect(percentage, current, total);
                }
            }
        );
        
        new Thread(new Runnable() {
            @Override
            public void run() {
            try {
                int totalFiles = selectedFiles.size();
                
                for (int i = 0; i < totalFiles; i++) {
                    File file = selectedFiles.get(i);
                    String inputPath = file.getAbsolutePath();
                    String outputPath = file.getParent();
                    
                    final int currentFile = i + 1;
                    final int progress = (int) ((currentFile * 100.0) / totalFiles);
                    currentProgress.set(progress);
                    
                    addLog("Processing file " + currentFile + "/" + totalFiles + ": " + file.getName());
                    updateProgress(progress, currentFile, totalFiles);
                    
                    try {
                        int result = decompileFiles(
                            inputPath, outputPath,
                            options.forceOverwrite,
                            options.ignoreDebugInfo,
                            options.minimizeDiffs,
                            options.unrestrictedAscii,
                            options.silentAssertions,
                            options.extensionFilter
                        );
                        
                        addLog("Process of decompiling: " + file.getName() + " is finished");
                    } catch (Exception e) {
                        addLog("Exception while decompiling " + file.getName() + ": " + e.getMessage());
                        Log.e(TAG, "Error decompiling file: " + file.getName(), e);
                    }
                }
                
                addLog("Decompilation process completed!");
                updateProgress(100, "Decompilation completed successfully!");
                
            } catch (Exception e) {
                addLog("Fatal error during decompilation: " + e.getMessage());
                Log.e(TAG, "Decompilation error", e);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "Ошибка декомпиляции: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
        }).start();
    }


    public static void cleanup() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
        progressText = null;
        logText = null;
        progressBar = null;
        logScrollView = null;
        if (logBuilder != null) {
            logBuilder.setLength(0);
        }
        if (currentProgress != null) {
            currentProgress.set(0);
        }
    }
    
    public static String getLogs() {
        return logBuilder != null ? logBuilder.toString() : "";
    }

    public static int getCurrentProgress() {
        return currentProgress != null ? currentProgress.get() : 0;
    }
    
    private native void setNativeCallbacks(Object progress, Object error, Object progressUpdate);
    private native int decompileFiles(String inputPath, String outputPath, 
                                    boolean forceOverwrite, boolean ignoreDebugInfo,
                                    boolean minimizeDiffs, boolean unrestrictedAscii,
                                    boolean silentAssertions, String extensionFilter);
    private native boolean isFileHaveBC(String path);
}
