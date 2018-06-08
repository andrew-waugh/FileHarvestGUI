/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fileharvestgui;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.web.WebView;

/**
 * FXML Controller class
 *
 * @author Andrew
 */
public class FXMLHelpController implements Initializable {
    private static final String HTML_PATH = "/help/index.html";
    private String hashTag;    
    
    @FXML
    protected ScrollPane webViewScrollPane;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {              
    }
    
    public void loadContent() {
        WebView webView = new WebView();
        webView.setFontSmoothingType(FontSmoothingType.GRAY);
        URL url = getClass().getResource(HTML_PATH);
        String htmlPath = url.toExternalForm();
        
        if (hashTag != null && hashTag.length() > 0) {
            if (hashTag.charAt(0) != '#') htmlPath += "#";            
            htmlPath += hashTag;
        }        
        
        webView.getEngine().load(htmlPath);
        webViewScrollPane.setContent(webView); 
    }
    
    public void setHashTag(String str) {        
        this.hashTag = str;
    }
    public String getHashTag() {
        return this.hashTag;
    }
    
}
