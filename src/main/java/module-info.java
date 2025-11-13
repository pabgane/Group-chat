module edu.ijse.clientchatgroup {
    requires javafx.controls;
    requires javafx.fxml;


    opens edu.ijse.clientchatgroup to javafx.fxml;
    exports edu.ijse.clientchatgroup;
}