/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fileharvestgui;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * FXML Controller class
 *
 * @author Andrew
 */
public class FXMLAboutController implements Initializable {

    @FXML
    private AnchorPane mainAnchorPane;
    @FXML
    private Text titleText;
    @FXML
    private Text subtitleText;
    @FXML
    private Label versionLabel;
    @FXML
    private Label lastUpdatedLabel;
    @FXML
    private ScrollPane webViewScrollPane;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // TODO
    }

    @FXML
    private void handleCloseAction(ActionEvent event) {
        final Stage stage = (Stage) mainAnchorPane.getScene().getWindow();
        stage.close();
    }

}
