/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fileharvestgui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.json.simple.JSONObject;

/**
 * FXML Controller class
 *
 * @author Andrew
 */
public class FXMLProgressController extends BaseVEOController implements Initializable {

    @FXML
    private AnchorPane rootAP;
    @FXML
    private ListView<String> reportLV;
    @FXML
    private ProgressBar constVEOsPB;
    @FXML
    private Label veosConstructedL;
    @FXML
    private Label veosFailedL;
    @FXML
    private Button finishB;
    @FXML
    private TextField logFileTF;
    @FXML
    private Label statusL;

    Job job;                    // information shared between scenes
    HostServices hostServices;
    private ObservableList<String> responses; // list of results generated
    CreateVEOsService cvs;      // Created to handle processing

    /**
     * Initializes the controller class.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        responses = FXCollections.observableArrayList();
        reportLV.setItems(responses);
        constVEOsPB.setProgress(0);

        try {
            initTooltips();
        } catch (AppFatal af) {
            System.err.println(af.getMessage());
        }
    }

    /**
     * Called when it is necessary to close this window
     */
    public void shutdown() {
        cvs.cancel();
        final Stage stage = (Stage) rootAP.getScene().getWindow();
        stage.close();
    }

    /**
     * Put a tool tip on each control
     */
    private void initTooltips() throws AppFatal {
        JSONObject json = openTooltips();
        createTooltip(reportLV, (String) json.get("report"));
        createTooltip(constVEOsPB, (String) json.get("progress"));
        createTooltip(veosConstructedL, (String) json.get("constructed"));
        createTooltip(veosFailedL, (String) json.get("failed"));
        createTooltip(finishB, (String) json.get("finish"));
        createTooltip(logFileTF, (String) json.get("logFile"));
    }

    /**
     * Carry out the instructions from the setup screen
     *
     * @param job information about the VEOs to be created
     * @param baseDirectory the base directory
     */
    public void generate(Job job, File baseDirectory) {

        this.job = job;
        this.baseDirectory = baseDirectory;
        cvs = new CreateVEOsService();
        cvs.start();
    }

    /**
     * Callback when user presses 'Finish' button
     */
    @FXML
    private void handleCloseAction(ActionEvent event) throws Exception {
        shutdown();
    }

    /**
     * Callback when user presses 'Browse' button for a logfile.
     */
    @FXML
    private void logfileBrowse(ActionEvent event) {
        File f;

        f = browseForSaveFile("Select Log file", null);
        if (f == null) {
            return;
        }
        job.logFile = f.toPath();
        logFileTF.setText(job.logFile.toString());
    }

    /**
     * Callback when user presses the 'Save' button for a logfile
     *
     * @param event
     */
    @FXML
    private void saveLogfile(ActionEvent event) {
        FileWriter fw;
        BufferedWriter bw;
        int i;

        // abort if no log file has been specified
        if (job.logFile == null) {
            return;
        }

        // open log file
        try {
            fw = new FileWriter(job.logFile.toFile());
        } catch (IOException ioe) {
            // popup message
            return;
        }
        bw = new BufferedWriter(fw);

        // write log entries
        for (i = 0; i < responses.size(); i++) {
            try {
                bw.write(responses.get(i));
                bw.write("\n");
            } catch (IOException ioe) {
                System.err.println("Failed writing log file: " + ioe.getMessage());
            }
        }

        // close log file
        try {
            bw.close();
        } catch (IOException ioe) {
            /* ignore */ }
        try {
            fw.close();

        } catch (IOException ioe) {
            /* ignore */ }
    }

    /**
     * Create a service that creates VEOs
     */
    private class CreateVEOsService extends Service<ObservableList<String>> {

        // create the task (i.e. thread) that actually creates the VEOs
        @Override
        protected Task<ObservableList<String>> createTask() {
            CreateVEOsTask veoTask;

            veoTask = new CreateVEOsTask(job, reportLV, constVEOsPB, veosConstructedL, veosFailedL);

            // this event handler is called when the thread completes, and it
            // puts the response into the report list view and scrolls to the
            // end
            veoTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, (WorkerStateEvent event) -> {
                reportLV.getItems().addAll(getValue());
                reportLV.scrollTo(reportLV.getItems().size() - 1);
                constVEOsPB.setProgress(1);
            });
            return veoTask;
        }

    }

    /**
     * Create a task (i.e. a thread) that actually creates the VEOs in order to
     * ensure that the GUI remains responsive
     */
    private class CreateVEOsTask extends Task<ObservableList<String>> {

        final Job job;
        final ListView<String> lv;  // the list view that will display the logging results
        final ProgressBar pb;       // the progress bar
        final Label successCnt;
        int veosConstCnt;           // number of VEOs constructed successfully
        final Label failCnt;
        int veosFailedCnt;          // number of VEOs that failed construction
        FileHarvest fh;             // Encapsulation of the file harvest itself

        public CreateVEOsTask(Job job, ListView<String> lv, ProgressBar pb, Label successCnt, Label failCnt) {
            this.job = job;
            // partialResults = new ReadOnlyObjectWrapper<>(this, "patrialResults", FXCollections.observableArrayList());
            this.lv = lv;
            this.pb = pb;
            this.successCnt = successCnt;
            this.failCnt = failCnt;
            veosConstCnt = 0;
            veosFailedCnt = 0;
        }

        /**
         * Actually create one or more VEOs. The parameters of the creation have
         * been provided when the task was created in the Job argument. The
         *
         * @return a list of strings containing the results of the creation
         */
        @Override
        protected ObservableList<String> call() {
            ObservableList<String> results; // list of results generated
            LogHandler lh;
            int i;

            // create a list in which to put the responses 
            results = FXCollections.observableArrayList();
            updateValue(results);

            try {
                lh = new LogHandler(results);
                fh = new FileHarvest(job, lh);
            } catch (AppFatal af) {
                results.addAll("FAILED: " + af.getMessage());
                return results;
            }

            // go through list of directories, pausing after each to deal with
            // results and check if we've been cancelled
            // System.out.println("Processing size: " + job.items.size());
            for (i = 0; i < job.items.size() && !isCancelled(); i++) {
                // System.out.println("Processing " + job.items.get(i));
                results.addAll("Starting " + job.items.get(i));
                Platform.runLater(() -> {
                    lv.getItems().addAll(results);
                    results.clear();
                    lv.scrollTo(lv.getItems().size() - 1);
                });
                if (fh.process(Paths.get(job.items.get(i)))) {
                    veosConstCnt++;
                } else {
                    veosFailedCnt++;
                }
                double j = ((double) i + 1) / job.items.size();
                Platform.runLater(() -> {
                    lv.getItems().addAll(results);
                    results.clear();
                    lv.scrollTo(lv.getItems().size() - 1);
                    pb.setProgress(j);
                    successCnt.setText(Integer.toString(veosConstCnt));
                    failCnt.setText(Integer.toString(veosFailedCnt));
                });
            }
            Platform.runLater(() -> {
                statusL.setText("Finished");
            });
            return results;
        }
    }

    private class LogHandler extends Handler {

        final SimpleFormatter sf;
        ObservableList<String> responses; // list of results generated

        public LogHandler(ObservableList<String> responses) {
            this.responses = responses;
            sf = new SimpleFormatter();
        }

        @Override
        public void publish(LogRecord record) {
            String s;

            s = sf.format(record);
            responses.addAll(s);
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() {

        }
    }

}
