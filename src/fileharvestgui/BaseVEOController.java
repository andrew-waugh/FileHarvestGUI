/*
 * Copyright Public Record Office Victoria 2017
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Peter Samaras
 * Version 1.0 June 2017
 */
package fileharvestgui;

import java.io.File;

import javafx.application.HostServices;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Base FXML controller for controlling VEO creation, analysis and re-signing.
 */
public class BaseVEOController {

    protected Stage preloaderStage;
    FXMLPreloaderController preloaderController;

    private HostServices hostServices;

    protected String helpHashTag;

    @FXML
    protected AnchorPane mainAnchorPane;

    protected File outputFolderFile;
    public File baseDirectory;  // directory to use when starting a browse window

    /**
     * Put a tool tip on each control
     */
    private static final String TOOLTIPS_PATH = "assets/tooltips.json";

    /**
     * set up JSON reader for the tooltips
     *
     * @return JSONObject to read the tool tips from
     * @throws fileharvestgui.AppFatal if the Tooltip file cannot be opened or
     * read
     */
    protected JSONObject openTooltips() throws AppFatal {
        JSONParser parser;
        File tooltipsFile = new File(TOOLTIPS_PATH);
        FileReader fileReader;
        JSONObject allJson, json;

        parser = new JSONParser();
        try {
            fileReader = new FileReader(tooltipsFile);
        } catch (FileNotFoundException e) {
            throw new AppFatal("Tooltips file not found: " + e.getMessage());
        }
        try {
            allJson = (JSONObject) parser.parse(fileReader);
        } catch (IOException | ParseException e) {
            throw new AppFatal("Failed reading or parsing Tooltips file: " + e.getMessage());
        }
        try {
            fileReader.close();
        } catch (IOException ioe) {
            // ignore
        }
        json = (JSONObject) allJson.get("create");
        return json;
    }

    /**
     * Create a Tooltip for a control
     *
     * @param control control tool tip is to be associated with
     * @param text the text in the tool tip
     */
    public void createTooltip(Control control, String text) {
        Tooltip tooltip = new Tooltip();
        tooltip.setMaxWidth(300);
        tooltip.setWrapText(true);
        tooltip.setText(text);
        control.setTooltip(tooltip);
    }

    private void openUrl(String url) throws Exception {
        if (hostServices == null) {
            throw new Exception("Host Services is undefined");
        }
        hostServices.showDocument(url);
    }

    @FXML
    protected void handleBrowseOutputFolderAction(ActionEvent event) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Output Folder");
        if (outputFolderFile != null) {
            File initialFolderFile = outputFolderFile;
            if (!initialFolderFile.exists()) {
                initialFolderFile = initialFolderFile.getParentFile();
            }
            if (initialFolderFile != null && initialFolderFile.exists()) {
                dirChooser.setInitialDirectory(initialFolderFile);
            } else if (baseDirectory != null) {
                dirChooser.setInitialDirectory(baseDirectory);
            }
        }
        File tempOutputFolderFile = dirChooser.showDialog(null);

        if (tempOutputFolderFile != null) {
            baseDirectory = tempOutputFolderFile;
            outputFolderFile = tempOutputFolderFile;
            // outputFolderField.setText(outputFolderFile.getPath());
        }
    }

    /**
     * Browse for a directory
     *
     * @param title title of browse window
     * @param initialDir initial directory (might be null)
     * @return selected directory (initial directory if none selected)
     */
    protected File browseForDirectory(String title, File initialDir) {
        DirectoryChooser dirChooser;
        File selectedDir;

        // sanity check...
        if (initialDir == null && baseDirectory == null) {
            return null;
        }

        // if initialDir not specified, use the last directory navigated to
        if (initialDir == null) {
            initialDir = baseDirectory;
        }

        // if the directory doesn't exist, go up until we get to the root or one does exist
        while (initialDir != null && !initialDir.exists()) {
            initialDir = initialDir.getParentFile();
        }

        // set up Directory Chooser
        dirChooser = new DirectoryChooser();
        dirChooser.setTitle(title);
        if (initialDir != null && initialDir.exists()) {
            dirChooser.setInitialDirectory(initialDir);
        } else {
            dirChooser.setInitialDirectory(new File("/"));
        }

        // user selects directory
        selectedDir = dirChooser.showDialog(null);
        if (selectedDir == null) {
            return null;
        }

        // remember this directory for next time
        baseDirectory = selectedDir.getParentFile();
        if (baseDirectory == null) {
            baseDirectory = new File("/");
        }
        return selectedDir;
    }

    protected File browseForOpenFile(String title, File file) {
        return browseFile(title, file, true);
    }

    protected File browseForSaveFile(String title, File file) {
        return browseFile(title, file, false);
    }

    protected File browseFile(String title, File file, boolean openFile) {
        FileChooser fileChooser;
        File parentFile;
        String fileName;
        File chosenFile;

        // sanity check...
        if (file == null && baseDirectory == null) {
            return null;
        }

        fileChooser = new FileChooser();
        fileChooser.setTitle(title);

        if (file != null) {
            if ((fileName = file.getName()) != null) {
                fileChooser.setInitialFileName(fileName);
            }
            parentFile = file.getParentFile();
            if (parentFile != null && parentFile.exists()) {
                fileChooser.setInitialDirectory(parentFile);
            } else if (baseDirectory != null) {
                fileChooser.setInitialDirectory(baseDirectory);
            } else {
                fileChooser.setInitialDirectory(new File("/"));
            }
        } else if (baseDirectory != null) {
            fileChooser.setInitialDirectory(baseDirectory);
        } else {
            fileChooser.setInitialDirectory(new File("/"));
        }

        //user chooses file
        chosenFile = openFile ? fileChooser.showOpenDialog(null)
                : fileChooser.showSaveDialog(null);
        if (chosenFile == null) {
            return null;
        }

        // remember this directory for next time
        baseDirectory = chosenFile.getParentFile();
        if (baseDirectory == null) {
            baseDirectory = new File("/");
        }
        return chosenFile;
    }

    public Alert alertWarning(String title, String headerText, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(message);
        return alert;
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

}
