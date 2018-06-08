/*
 * Copyright Public Record Office Victoria 2017
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Peter Samaras
 * Version 1.0 June 2017
 */
package fileharvestgui;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Level;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.io.PrintWriter;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import javafx.scene.control.TextArea;


/**
 * Handles logging to a file, a TextArea control and potentially other targets 
 * for logging such as to the console via standard output and/or error stream.
 */
public class LogHandler extends Handler {
    
    // Store messages with their log level for filtering
    private final List<LogRecord> log = new ArrayList<>();

    private TextArea target = null;
    private File logFile = null;    
     
    public LogHandler(TextArea textArea, File logFile) {
        super();
        target = textArea;
        this.logFile = logFile;        
    }
    
    @Override
    public void publish(LogRecord logRecord) {
        log.add(logRecord);
        this.writeLog(logRecord);
    }

    @Override
    public void flush() {  
    }

    @Override
    public void close() {   
    }
    
    public void writeLog(LogRecord logRecord) {
        String str = logRecord.getLevel() + ": ";
        str += logRecord.getMessage();
        
        try {
            target.appendText(str + "\n");
        }
        catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
                
        if (logFile != null) {    
            FileWriter fw = null;
            PrintWriter pw = null;            
            try {                
                fw = new FileWriter(logFile, true);
                pw = new PrintWriter(fw);                
                pw.println(str);                
            }
            catch (IOException e) {
                e.printStackTrace();                
            } finally {
                try {                    
                    if (pw != null) pw.close();
                    if (fw != null) fw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public void filterLogByLevels(List<Level> levels) {
        target.setText("");
        
        Iterator<LogRecord> logRecordsIt = log.iterator();
        while(logRecordsIt.hasNext()) {
            LogRecord logRecord = logRecordsIt.next();
            
            Iterator<Level> levelsIt = levels.iterator();
            while(levelsIt.hasNext()) {
                Level level = levelsIt.next();
                if (level.equals(logRecord.getLevel())) {
                    writeLog(logRecord);
                    break;
                }
            }            
        }
    }
}