module com.fieldstory.farm {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.databind;

    // P1 SQLite 正式存档：JDBC API 与 SQLite 驱动（模块名 org.xerial.sqlitejdbc）
    requires java.sql;
    requires org.xerial.sqlitejdbc;


    opens com.fieldstory.farm to javafx.fxml;
    exports com.fieldstory.farm;

    // FXML 反射需要访问 controller 与 view 包
    opens com.fieldstory.farm.controller to javafx.fxml;
    opens com.fieldstory.farm.view to javafx.fxml;

    // javafx.graphics 需要反射实例化 Application 子类
    exports com.fieldstory.farm.view to javafx.graphics;
}