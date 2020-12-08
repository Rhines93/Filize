package com.example.filemanager;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout1);
    }

    // Declare global variables

    private boolean isFileManagerInitialized;

    private boolean[] selection;
    private int selectedItemIndex;

    private File dir;
    private File[] files;
    private List<String> filesList;
    private int filesFoundCount;
    private String currentPath;

    private String copyPath;

    private Button refreshButton;

    private boolean isLongClick;
    // Method which will run most of our functionality upon opening/resuming application

    @Override
    protected void onResume(){

        // Check if permissions are granted upon opening or resuming application

        super.onResume();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && arePermissionsDenied()) {
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS);
            return;
        }

        if(!isFileManagerInitialized) {

            currentPath = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

            final String rootPath = currentPath.substring(0, currentPath.lastIndexOf('/'));

            dir = new File(currentPath);
            files = dir.listFiles();

            final TextView pathOutput = findViewById(R.id.pathOutput);

            // Set display text for current directory

            pathOutput.setText(currentPath.substring(currentPath.lastIndexOf('/')+1));

            filesFoundCount = files.length;

            // Display every file found in the downloads folder as a list in the app

            final ListView listView = findViewById(R.id.listView);
            final TextAdapter textAdapter1 = new TextAdapter();
            listView.setAdapter(textAdapter1);

            filesList = new ArrayList<>();

            // Increment loop for every file found and add their path to files list

            for(int i = 0; i < filesFoundCount; i++){
                filesList.add(String.valueOf(files[i].getAbsolutePath()));
            }

            textAdapter1.setData(filesList);

            selection = new boolean[files.length];

            // Create a button to refresh the current working directory

            refreshButton = findViewById(R.id.refresh);
            refreshButton.setOnClickListener(v -> {

                files = dir.listFiles();

                if(files==null) {
                    return;
                }

                filesFoundCount = files.length;
                filesList.clear();
                for(int i = 0; i < filesFoundCount; i++) {
                    filesList.add(String.valueOf(files[i].getAbsolutePath()));
                }
                textAdapter1.setData(filesList);
            });

            // Button to return to parent folder

            final Button goToParentFolder = findViewById(R.id.parentFolder);
            goToParentFolder.setOnClickListener(v -> {

                if(currentPath.equals(rootPath)) {
                    return;
                }

                currentPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
                dir = new File(currentPath);
                pathOutput.setText(currentPath.substring(currentPath.lastIndexOf('/')+1));
                refreshButton.callOnClick();
                selection = new boolean [files.length];
                textAdapter1.setSelection(selection);
            });

            listView.setOnItemClickListener((parent, view, position, id) -> new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(!isLongClick) {
                        if(position>files.length){
                            return;
                        }
                        if(files[position].isDirectory()) {
                            currentPath = files[position].getAbsolutePath();
                            dir = new File(currentPath);
                            pathOutput.setText(currentPath.substring(currentPath.lastIndexOf('/') + 1));
                            refreshButton.callOnClick();
                            selection = new boolean [files.length];
                            textAdapter1.setSelection(selection);
                        }
                    }
                }
            },100));

            // Set listener to select items when a long click is initiated

            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                isLongClick = true;
                selection[position] = !selection[position];
                textAdapter1.setSelection(selection);

                // Check if at least one or more items is selected and if so show the button bar

                int selectionCount = 0;

                for (boolean b : selection) {
                    if (b) {
                        selectionCount++;
                    }
                }
                if(selectionCount > 0) {
                    if(selectionCount==1) {
                        selectedItemIndex = position;
                        findViewById(R.id.rename).setVisibility(View.VISIBLE);
                    } else {
                        findViewById(R.id.rename).setVisibility(View.GONE);
                    }
                    findViewById(R.id.bottomBar).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.bottomBar).setVisibility(View.GONE);
                }
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isLongClick = false;
                    }
                },1000);
                return false;
            });

            // Setup delete button and confirmation for files and folders

            final Button deleteButton = findViewById(R.id.delete);

            deleteButton.setOnClickListener(v -> {

                final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(MainActivity.this);

                deleteDialog.setTitle("Delete");
                deleteDialog.setMessage("Would you like to permanently delete selected files?");

                deleteDialog.setPositiveButton("Yes", (dialog, which) -> {
                    for(int i = 0; i < files.length; i++) {
                        if(selection[i]) {
                            deleteContent(files[i]);
                            selection[i]=false;
                        }
                    }
                    refreshButton.callOnClick();
                });
                deleteDialog.setNegativeButton("No", (dialog, which) -> dialog.cancel());

                deleteDialog.show();
            });

            final Button createNewFolder = findViewById(R.id.newFolder);

            createNewFolder.setOnClickListener(v -> {
                final AlertDialog.Builder newFolderDialog = new AlertDialog.Builder(MainActivity.this);
                newFolderDialog.setTitle("New Folder");
                final EditText input = new EditText(MainActivity.this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                newFolderDialog.setView(input);

                newFolderDialog.setPositiveButton("Yes", (dialog, which) -> {
                    File newFolder = new File(currentPath+"/"+input.getText());
                    if(!newFolder.exists()){
                        newFolder.mkdir();
                        refreshButton.callOnClick();
                    } else {
                        newFolder = new File(currentPath+"/"+input.getText()+"(1)");
                        newFolder.mkdir();
                        refreshButton.callOnClick();
                    }
                });
                newFolderDialog.setNegativeButton("No", (dialog, which) -> dialog.cancel());
                newFolderDialog.show();
            });

            final Button renameButton = findViewById(R.id.rename);
            renameButton.setOnClickListener(v -> {

                final AlertDialog.Builder renameDialog = new AlertDialog.Builder(MainActivity.this);
                renameDialog.setTitle("Rename to:");

                // Create an EditText variable using Java.util.objects

                final EditText input = new EditText(MainActivity.this);

                // I use the setText() function from the TextView API

                // Acquire the path of the selected object you want to rename
                // it stores the absolute path of that object
                // Then receive the user input and modify the path of the original object
                // with the updated name of the file

                String renamePath = files[selectedItemIndex].getAbsolutePath();
                input.setText(renamePath.substring(renamePath.lastIndexOf('/')));

                input.setInputType(InputType.TYPE_CLASS_TEXT);
                renameDialog.setView(input);

                renameDialog.setPositiveButton("Rename", (dialog, which) -> {
                    String s = new File(renamePath).getParent() + "/" + input.getText();
                    File newFile = new File(s);
                    new File(renamePath).renameTo(newFile);
                    refreshButton.callOnClick();
                    selection = new boolean[files.length];
                    textAdapter1.setSelection(selection);
                });
                renameDialog.show();
            });

            final Button copyButton = findViewById(R.id.Copy);
            copyButton.setOnClickListener(v -> {

                /*
                Acquire path of selected object you wish to copy
                Then navigate to another folder
                and run the copy method which takes the previously acquired path
                and uses it as an inputstream which gets read and re-written through
                outputstream to our destination folder
                 */

                copyPath = files[selectedItemIndex].getAbsolutePath();
                selection = new boolean[files.length];
                textAdapter1.setSelection(selection);
                findViewById(R.id.Paste).setVisibility(View.VISIBLE);
            });

            final Button pasteButton = findViewById(R.id.Paste);
            pasteButton.setOnClickListener(v -> {
                pasteButton.setVisibility(View.GONE);
                String pastePath = currentPath + copyPath.substring(copyPath.lastIndexOf('/'));
                copy(new File(copyPath), new File(pastePath));
                files = new File(currentPath).listFiles();
                selection = new boolean[files.length];
                textAdapter1.setSelection(selection);
                refreshButton.callOnClick();
            });


            isFileManagerInitialized = true;
        } else {
            refreshButton.callOnClick();
        }
    }

    // Create copy method for copy button

    private void copy(File source, File dest) {
        try {
            byte[] buffer = new byte[1024];
            InputStream in = new FileInputStream(source);
            OutputStream out = new FileOutputStream(dest);
            int length;

            // Copies input read and writes it to output

            while((length = in.read(buffer)) > 0){
                out.write(buffer, 0, length);
            }

            // Close input and output stream to release system resources

            out.close();
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class TextAdapter extends BaseAdapter{

        private List<String> data = new ArrayList<>();

        private boolean[] selection;

        public void setData(List<String> data) {
            if(data != null) {
                this.data.clear();
                if(data.size() > 0) {
                    this.data.addAll(data);
                }
                notifyDataSetChanged();
            }
        }

        public void setSelection(boolean[] selection) {
            if(selection != null) {
                this.selection = new boolean[selection.length];
                for(int i = 0; i < selection.length; i++) {
                    this.selection[i] = selection[i];
                }
                notifyDataSetChanged();
            }
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public String getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView==null){
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false);
                convertView.setTag(new ViewHolder((TextView) convertView.findViewById(R.id.textItem)));
            }
            ViewHolder holder = (ViewHolder) convertView.getTag();
            final String item = getItem(position);

            // Set text display for each item within current directory

            holder.info.setText(item.substring(item.lastIndexOf('/')+1));

            // If item in directory is selected modify color to indicate such

            if(selection != null){
                if(selection[position]){
                    holder.info.setBackgroundColor(Color.argb(100, 15, 15, 15));
                } else {
                    holder.info.setBackgroundColor(Color.WHITE);
                }
            }
            return convertView;
        }

        class ViewHolder{
            TextView info;

            ViewHolder(TextView info){
                this.info = info;
            }
        }
    }

    // Begin Permissions Check & Request

    private static final int REQUEST_PERMISSIONS = 1234;

    private static final String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    // We are requesting 2 permissions, both read & write to external storage

    private static final int PERMISSIONS_COUNT = 2;

    // Create a method which checks if our permissions are denied

    @SuppressLint("NewApi")
    private boolean arePermissionsDenied(){
            int p = 0;
            while(p < PERMISSIONS_COUNT){
                if(checkSelfPermission(PERMISSIONS[p]) != PackageManager.PERMISSION_GRANTED){
                    return true;
                }
                p++;
            }
        return false;
    }

    // Method to delete individual files or folders containing multiple files

    private void deleteContent(File content) {

        /*
        If file selected is a directory then check if it is empty or has contents
        if empty, delete folder
        if non-empty folder, then loop through the contents and delete each one sequentially
        perform final check if folder is empty after deleting contents and, if empty, delete the folder
        */

        if(content.isDirectory()) {
            if(content.list().length != 0) {
                String[] files = content.list();
                for(String temp : files) {
                    File contentDel = new File(content, temp);
                    deleteContent(contentDel);
                }
                if(content.list().length==0) {
                    content.delete();
                }
            } else {
                content.delete();
            }
        } else {
            content.delete();
        }
    }

}
