/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fileharvestgui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * This class encapsulates the VEO generation
 *
 * @author Andrew
 */
public class Job {

    boolean verbose;                // true if verbose output
    boolean debug;                  // true if debugging output
    ObservableList<String> items;   // list of directories to make into VEOs
    Path templateDir;               // template directory selected
    Path outputDir;                 // output directory selected
    Path pfxFile;                   // PFX file to sign VEOs
    String pfxFilePassword;         // password of PFX file
    String hashAlg;                 // hash algorithm to use
    ObservableList<String> foldersToExclude; // list of subdirectories to exclude from harvest 
    ArrayList<String> filesToExclude; // list of patterns of file names to exclude from harvest
    Path archiveDescFile;           // contains an archival description of the harvest
    Path ignoreFile;                // file containing templates of files to ignore in harvest
    Path logFile;                   // file to save the log

    /**
     * Constructor
     */
    public Job() {
        verbose = AppConfig.getCreateVerboseOutputDefault();
        debug = AppConfig.getCreateDebugModeDefault();
        items = FXCollections.observableArrayList();
        templateDir = null;
        outputDir = Paths.get(AppConfig.getCreateOutputFolderDefault());
        pfxFile = null;
        pfxFilePassword = "";
        hashAlg = "SHA-512";
        foldersToExclude = FXCollections.observableArrayList();
        filesToExclude = new ArrayList<>();
        archiveDescFile = null;
        ignoreFile = null;
        logFile = null;
    }

    /**
     * Check to see if sufficient information has been entered to generate VEOs
     *
     * @return
     */
    public boolean validate() {
        if (items == null || items.size() == 0) {
            return false;
        }
        if (templateDir == null) {
            return false;
        }
        if (pfxFile == null) {
            return false;
        }
        if (pfxFilePassword == null || pfxFilePassword.equals("") || pfxFilePassword.trim().equals(" ")) {
            return false;
        }
        return true;
    }

    /**
     * Create a JSON file capturing the Job
     *
     * @param file
     * @throws AppError
     */
    public void saveJob(Path file) throws AppError {
        JSONObject j1, j2;
        JSONArray ja1;
        int i;

        FileWriter fw;
        BufferedWriter bw;

        try {
            fw = new FileWriter(file.toFile());
            bw = new BufferedWriter(fw);
        } catch (IOException ioe) {
            throw new AppError("Failed reading Job file: " + ioe.getMessage());
        }

        j1 = new JSONObject();
        if (templateDir != null) {
            j1.put("templateDirectory", templateDir.toString());
        }
        j1.put("verboseReporting", verbose);
        j1.put("debugReporting", debug);
        if (items != null && items.size() > 0) {
            ja1 = new JSONArray();
            for (i = 0; i < items.size(); i++) {
                j2 = new JSONObject();
                j2.put("item", items.get(i).toString());
                ja1.add(j2);
            }
            j1.put("itemsToBuild", ja1);
        }
        if (outputDir != null) {
            j1.put("outputDirectory", outputDir.toString());
        }
        if (pfxFile != null) {
            j1.put("pfxFile", pfxFile.toString());
        }
        if (pfxFilePassword != null) {
            j1.put("pfxPassword", pfxFilePassword);
        }
        if (hashAlg != null) {
            j1.put("hashAlgorithm", hashAlg);
        }
        if (logFile != null) {
            j1.put("logFile", logFile.toString());
        }
        if (foldersToExclude != null && foldersToExclude.size() > 0) {
            ja1 = new JSONArray();
            for (i = 0; i < foldersToExclude.size(); i++) {
                j2 = new JSONObject();
                j2.put("folder", foldersToExclude.get(i));
                ja1.add(j2);
            }
            j1.put("foldersToExclude", ja1);
        }
        if (filesToExclude != null && filesToExclude.size() > 0) {
            ja1 = new JSONArray();
            for (i = 0; i < filesToExclude.size(); i++) {
                j2 = new JSONObject();
                j2.put("filePattern", filesToExclude.get(i));
                ja1.add(j2);
            }
            j1.put("filesToExclude", ja1);
        }
        // archiveDescFile = null;
        // ignoreFile = null;

        try {
            bw.write(prettyPrintJSON(j1.toString()));
        } catch (IOException ioe) {
            throw new AppError("Failed trying to write Job file: " + ioe.getMessage());
        }
        try {
            bw.close();
            fw.close();
        } catch (IOException ioe) {
            /* ignore */ }
    }

    private String prettyPrintJSON(String in) {
        StringBuffer sb;
        int i, j, indent;
        char ch;

        sb = new StringBuffer();
        indent = 0;
        for (i = 0; i < in.length(); i++) {
            ch = in.charAt(i);
            switch (ch) {
                case '{':
                    indent++;
                    sb.append("{");
                    break;
                case '}':
                    indent--;
                    sb.append("}");
                    break;
                case '[':
                    indent++;
                    sb.append("[\n");
                    for (j = 0; j < indent; j++) {
                        sb.append(" ");
                    }
                    break;
                case ']':
                    indent--;
                    sb.append("]");
                    break;
                case ',':
                    sb.append(",\n");
                    for (j = 0; j < indent; j++) {
                        sb.append(" ");
                    }
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Read a JSON file describing the Job to run
     * @param file file containing the job file
     * @throws fileharvestgui.AppError
     */
    public void loadJob(Path file) throws AppError {
        JSONParser parser = new JSONParser();
        JSONObject j1, j2;
        JSONArray ja1;
        int i;
        FileReader fr;
        BufferedReader br;
        String s;

        try {
            fr = new FileReader(file.toFile());
            br = new BufferedReader(fr);
            j1 = (JSONObject) parser.parse(br);
        } catch (ParseException pe) {
            throw new AppError("Failed parsing Job file: " + pe.toString());
        } catch (IOException ioe) {
            throw new AppError("Failed reading Job file: " + ioe.getMessage());
        }

        if ((s = (String) j1.get("templateDirectory")) != null) {
            templateDir = Paths.get(s);
        }
        verbose = ((Boolean) j1.get("verboseReporting"));
        debug = ((Boolean) j1.get("debugReporting"));
        ja1 = (JSONArray) j1.get("itemsToBuild");
        if (ja1 != null) {
            items.clear();
            for (i = 0; i < ja1.size(); i++) {
                j2 = (JSONObject) ja1.get(i);
                items.add((String) j2.get("item"));
            }
        }
        if ((s = (String) j1.get("outputDirectory")) != null) {
            outputDir = Paths.get(s);
        }
        if ((s = (String) j1.get("pfxFile")) != null) {
            pfxFile = Paths.get(s);
        }
        pfxFilePassword = (String) j1.get("pfxPassword");
        hashAlg = (String) j1.get("hashAlgorithm");
        ja1 = (JSONArray) j1.get("foldersToExclude");
        if (ja1 != null) {
            foldersToExclude.clear();
            for (i = 0; i < ja1.size(); i++) {
                j2 = (JSONObject) ja1.get(i);
                foldersToExclude.add((String) j2.get("folder"));
            }
        }
        ja1 = (JSONArray) j1.get("filesToExclude");
        if (ja1 != null) {
            filesToExclude.clear();
            for (i = 0; i < ja1.size(); i++) {
                j2 = (JSONObject) ja1.get(i);
                filesToExclude.add((String) j2.get("filePattern"));
            }
        }
        archiveDescFile = null;
        ignoreFile = null;
        if ((s = (String) j1.get("logFile")) != null) {
            logFile = Paths.get(s);
        }

        try {
            br.close();
            fr.close();
        } catch (IOException ioe) {
            /* ignore */ }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Verbose:" + verbose);
        sb.append("Debug:" + debug);
        sb.append("Items:" + items);
        sb.append("TemplateDir:" + templateDir);
        sb.append("OutputDir:" + outputDir);
        sb.append("PFXFile:" + pfxFile);
        sb.append("PFXFilePassword:" + pfxFilePassword);
        // hashAlg = AppConfig.getCreateHashAlgorithmDefault();
        sb.append("HashAlg:" + hashAlg);
        sb.append("FolderExc:" + foldersToExclude);
        sb.append("FilesExc:" + filesToExclude);
        // sb.append("ArchiveDescFile:"+archiveDescFile);
        //sb.append("Verbose:"+ignoreFile);
        sb.append("LogFile:" + logFile);
        return sb.toString();
    }

    /**
     * Start the generation
     */
    public void generate() {
    }
}
