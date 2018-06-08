/*
 * Copyright Public Record Office Victoria 2017
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Peter Samaras
 * Version 1.0 June 2017
 */
package fileharvestgui;
 
import java.io.File;
import java.io.FileReader;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Manages the applications configuration
 */
public class AppConfig {
    
    private static final String CONFIG_PATH = "assets/config.json";    
    private static AppConfig appConfig;
    
    private static boolean isPreloaderEnabled = true;
    private static String windowTitle = "File Harvest App";
    // Send standard output to UI textarea
    private static boolean isLogOutputToUI = true;
    // Send standard errors to UI textarea
    private static boolean isLogErrorsToUI = true;
    
    // Create VEO config
    private static String createWindowTitle = "%window_title% - Create";
    private static String createSummaryWindowTitle = "%create_window_title% Summary";
    private static String createPreloaderText = "Creating...";
    private static String createOutputFolderDefault = "%user.home%VEOs";
    private static String createLogFilePathDefault = "%output_folder%\\logs\\veo_create.txt";
    private static boolean createVerboseOutputDefault = true;
    private static boolean createDebugModeDefault = false;
    private static String createHashAlgorithmDefault = "SHA-512";
    private static String createTransferModeDefault = "link";
    // Anchor tag for linking to the HTML VEO create help
    private static String createHelpHashTag = "#veocreate";
    
    
    public AppConfig() {
        File configFile = new File(CONFIG_PATH);
        loadJSON(configFile);
    }
    
    public static AppConfig getInstance() {
        if (appConfig == null) {
            appConfig = new AppConfig();
        }
        
        return appConfig;
    }
    
    private static void loadJSON(File configFile) {
        JSONParser parser = new JSONParser();        
        
        try {           
            FileReader fileReader = new FileReader(configFile);
            JSONObject allJson = (JSONObject) parser.parse(fileReader);
            
            isPreloaderEnabled = Boolean.parseBoolean(configValue(allJson, "preloader_enabled", String.valueOf(isPreloaderEnabled)));            
            windowTitle = configValue(allJson, "window_title", windowTitle);            
            
            JSONObject loggingJson = (JSONObject) allJson.get("logging");
            isLogOutputToUI = Boolean.parseBoolean(configValue(loggingJson, "output_to_ui", String.valueOf(isLogOutputToUI)));
            isLogErrorsToUI = Boolean.parseBoolean(configValue(loggingJson, "errors_to_ui", String.valueOf(isLogErrorsToUI)));
            
            processCreateConfig(allJson);
        } catch (Exception e) {
            e.printStackTrace();
        }    
    }
         
    private static String configValue(JSONObject json, String key, String defaultVal) {
        if (json == null || json.isEmpty()) {
            return defaultVal;
        }
        
        String val = json.get(key) != null ? String.valueOf(json.get(key)) : null;
        if (val != null) {            
            return val;
        }
        
        return defaultVal;
    }
    
    private static void processCreateConfig(JSONObject json) {
        JSONObject createJson = (JSONObject)json.get("create");
        
        createPreloaderText = configValue(createJson, "preloader_text", createPreloaderText);
        createWindowTitle = configValue(createJson, "window_title", createWindowTitle);
        createWindowTitle = createWindowTitle.replace("%window_title%", windowTitle);
        
        JSONObject summaryJson = createJson != null ? (JSONObject)createJson.get("summary") : null;
        createSummaryWindowTitle = configValue(summaryJson, "window_title", createSummaryWindowTitle);
        createSummaryWindowTitle = createSummaryWindowTitle.replace("%create_window_title%", createWindowTitle);
        
        JSONObject defaultsJson = createJson != null ? (JSONObject)createJson.get("defaults") : null;
        createOutputFolderDefault = configValue(defaultsJson, "output_folder", createOutputFolderDefault);
        createOutputFolderDefault = processConfigPath(createOutputFolderDefault);       
        createLogFilePathDefault = configValue(defaultsJson, "log_file_path", createLogFilePathDefault);
        createLogFilePathDefault = processConfigPath(createLogFilePathDefault);        
        createVerboseOutputDefault = Boolean.parseBoolean(configValue(defaultsJson, "verbose_output", String.valueOf(createVerboseOutputDefault)));
        createDebugModeDefault = Boolean.parseBoolean(configValue(defaultsJson, "debug_mode", String.valueOf(createDebugModeDefault)));
        createHashAlgorithmDefault = configValue(defaultsJson, "hash_algorithm", createHashAlgorithmDefault);
        createTransferModeDefault = configValue(defaultsJson, "transfer_mode", createTransferModeDefault);
        
        createHelpHashTag = configValue(createJson, "help_hash_tag", createHelpHashTag);
    }
            
    protected static String processConfigPath(String path) {
        String userHomePath = System.getProperty("user.home") + File.separator;
        path = path.replace("%user.home%", userHomePath);
        path = path.replace("%file.separator%", File.separator);
        
        return path;
    }
    
    public static void setPreloaderEnabled(boolean val) {
        isPreloaderEnabled = val;
    }
    
    public static boolean isPreloaderEnabled() {
        return isPreloaderEnabled;
    }
    
    public static void setLogOutputToUI(boolean val) {
        isLogOutputToUI = val;
    }
    
    public static boolean isLogOutputToUI() {
        return isLogOutputToUI;
    }
    
    public static void setLogErrorsToUI(boolean val) {
        isLogErrorsToUI = val;
    }
    
    public static boolean isLogErrorsToUI() {
        return isLogErrorsToUI;
    }
    
    public static void setWindowTitle(String title) {
        windowTitle = title;
    }
    
    public static String getWindowTitle() {
        return windowTitle;
    }
    
    public static void setCreateWindowTitle(String title) {
        createWindowTitle = title;
    }
    public static String getCreateWindowTitle() {
        return createWindowTitle;
    }
    
    public static void setCreateSummaryWindowTitle(String title) {
        createSummaryWindowTitle = title;
    }
    
    public static String getCreateSummaryWindowTitle() {
        return createSummaryWindowTitle;
    }
    
    public static void setCreatePreloaderText(String text) {
        createPreloaderText = text;
    }
    
    public static String getCreatePreloaderText() {
        return createPreloaderText;
    }
    
    public static void setCreateOutputFolderDefault(String path) {
        createOutputFolderDefault = path;
    }
    
    public static String getCreateOutputFolderDefault() {
        return createOutputFolderDefault;
    }
    
    public static void setCreateLogFilePathDefault(String path) {
        createLogFilePathDefault = path;
    }
    
    public static String getCreateLogFilePathDefault() {
        return createLogFilePathDefault;
    }
    
    public static void setCreateVerboseOutputDefault(boolean val) {
        createVerboseOutputDefault = val;
    }
    
    public static boolean getCreateVerboseOutputDefault() {
        return createVerboseOutputDefault;
    }
    
    public static void setCreateDebugModeDefault(boolean val) {
        createDebugModeDefault = val;
    }
    
    public static boolean getCreateDebugModeDefault() {
        return createDebugModeDefault;
    }
    
    public static void setCreateHashAlgorithmDefault(String hashAlgorithm) {
        createHashAlgorithmDefault = hashAlgorithm;
    }
    
    public static String getCreateHashAlgorithmDefault() {
        return createHashAlgorithmDefault;
    }
    
    public static void setCreateTransferModeDefault(String transferMode) {
        createTransferModeDefault = transferMode;
    }
    
    public static String getCreateTransferModeDefault() {
        return createTransferModeDefault;
    }
    
    public static void setCreateHelpHashTag(String hashTag) {
        createHelpHashTag = hashTag;
    }
    
    public static String getCreateHelpHashTag() {
        return createHelpHashTag;
    }
}