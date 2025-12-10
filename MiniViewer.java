import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;
import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;

/**
 * User roles for role-based access control
 */
enum UserRole {
    GUEST("Guest", new String[]{"Load Tables", "FILTER", "Add Filter", "Clear Filter"}),
    CURATOR("Curator", new String[]{"Load Tables", "CREATE Table", "INSERT Row", "UPDATE Row", "DELETE Row", "FILTER", "Add Filter", "Clear Filter"}),
    ADMIN("Admin", new String[]{"Load Tables", "CREATE Table", "INSERT Row", "UPDATE Row", "DELETE Row", "FILTER", "Add Filter", "Clear Filter"});

    private final String displayName;
    private final String[] permissions;

    UserRole(String displayName, String[] permissions) {
        this.displayName = displayName;
        this.permissions = permissions;
    }

    public String getDisplayName() { return displayName; }
    public String[] getPermissions() { return permissions; }

    public boolean hasPermission(String buttonLabel) {
        for (String perm : permissions) {
            if (perm.equals(buttonLabel)) return true;
        }
        return false;
    }
}

/**
 * MiniViewer.java
 *
 * Standalone example GUI that connects to a SQLite database and loads an
 * entire table into a JTable when the user clicks the "Load Table" button.
 *
 * Usage:
 * 1) Download the sqlite-jdbc jar (xerial) and place it in a `lib` directory.
 *    Example: lib\sqlite-jdbc-3.42.0.0.jar
 * 2) Compile:
 *    javac -cp ".;lib\sqlite-jdbc-3.42.0.0.jar" MiniViewer.java
 * 3) Run (database path optional, defaults to painting.db in cwd):
 *    java -cp ".;lib\sqlite-jdbc-3.42.0.0.jar" MiniViewer [path\to\painting.db]
 *
 * The example is intentionally small and safe: it uses a whitelist of allowed
 * table names and runs SELECT * FROM <table> to populate a Swing table model.
 */
public class MiniViewer extends JFrame {
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JTable artistTable = new JTable();
    private final JTable workTable = new JTable();
    private final JTable museumTable = new JTable();
    private final JTable museumHoursTable = new JTable();
    private final JTable subjectTable = new JTable();
    private final JTable workSubjectTable = new JTable();
    private final JTable productSizeTable = new JTable();
    private final JTable canvasSizeTable = new JTable();
    private final JTable imageLinkTable = new JTable();
    
    private DefaultTableModel artistOriginal; // For filtering
    private DefaultTableModel workOriginal;
    private DefaultTableModel museumOriginal;
    private DefaultTableModel museumHoursOriginal;
    private DefaultTableModel subjectOriginal;
    private DefaultTableModel workSubjectOriginal;
    private DefaultTableModel productSizeOriginal;
    private DefaultTableModel canvasSizeOriginal;
    private DefaultTableModel imageLinkOriginal;
    // Map museum id -> unique color for color-coding related rows/cells
    private java.util.Map<Integer, java.awt.Color> museumColorMap = new java.util.HashMap<>();
    
    private String activeFilter = null; // Store active filter value
    private String filterColumnName = null; // Store active filter column
    private String activeFilterTableName = null; // Store which table was filtered
    
    UserRole currentUserRole = null; // Current logged-in user's role
    
    private final JComboBox<String> tableCombo;
    private final JButton loadButton = new JButton("Load Tables");
    private final JButton createButton = new JButton("CREATE Table");
    private final JButton insertButton = new JButton("INSERT Row");
    private final JButton updateButton = new JButton("UPDATE Row");
    private final JButton deleteButton = new JButton("DELETE Row");
    private final JButton filterButton = new JButton("FILTER");
    private final JButton addFilterButton = new JButton("Add Filter");
    private final JButton clearFilterButton = new JButton("Clear Filter");
    private final JButton logoutButton = new JButton("LOGOUT");
    private final JLabel statusLabel = new JLabel(" ");

    // Whitelist of tables in your project
    private static final List<String> ALLOWED_TABLES = Arrays.asList(
            "artist","work","museum","museum_hours","canvas_size",
            "product_size","image_link","subject","work_subject"
    );

    private final String dbUrl;

    public MiniViewer(String dbPath) {
        super("Mini Viewer — Paintings DB (Cascade Filter)");
        if (dbPath == null || dbPath.isEmpty()) dbPath = "painting.db";
        this.dbUrl = "jdbc:sqlite:" + dbPath;

        // Initialize tableCombo first (before potential early exit)
        tableCombo = new JComboBox<>(ALLOWED_TABLES.toArray(new String[0]));

        // Show login dialog
        showLoginDialog();
        
        // If user cancelled login, exit
        if (currentUserRole == null) {
            System.exit(0);
            return;
        }

        // Build UI
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Table:"));
        top.add(tableCombo);
        top.add(loadButton);
        top.add(createButton);
        top.add(insertButton);
        top.add(updateButton);
        top.add(deleteButton);
        top.add(filterButton);
        top.add(addFilterButton);
        top.add(clearFilterButton);
        top.add(logoutButton);
        top.add(statusLabel);

        // Add tables to tabs (include related tables)
        tabbedPane.addTab("Artist", new JScrollPane(artistTable));
        tabbedPane.addTab("Work", new JScrollPane(workTable));
        tabbedPane.addTab("Museum", new JScrollPane(museumTable));
        tabbedPane.addTab("Museum Hours", new JScrollPane(museumHoursTable));
        tabbedPane.addTab("Subject", new JScrollPane(subjectTable));
        tabbedPane.addTab("Work_Subject", new JScrollPane(workSubjectTable));
        tabbedPane.addTab("Product_Size", new JScrollPane(productSizeTable));
        tabbedPane.addTab("Canvas_Size", new JScrollPane(canvasSizeTable));
        tabbedPane.addTab("Image_Link", new JScrollPane(imageLinkTable));

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Wire buttons
        loadButton.addActionListener(this::onLoadClicked);
        createButton.addActionListener(this::onCreateClicked);
        insertButton.addActionListener(this::onInsertClicked);
        updateButton.addActionListener(this::onUpdateClicked);
        deleteButton.addActionListener(this::onDeleteClicked);
        filterButton.addActionListener(this::onFilterClicked);
        addFilterButton.addActionListener(this::onAddFilterClicked);
        clearFilterButton.addActionListener(this::onClearFilterClicked);
        logoutButton.addActionListener(this::onLogoutClicked);
        
        // Update button visibility based on user role
        updateButtonVisibility();

        // Show any uncaught exceptions (including EDT) in a dialog so we can debug
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            java.io.StringWriter sw = new java.io.StringWriter();
            ex.printStackTrace(new java.io.PrintWriter(sw));
            System.err.println(sw.toString());
            JOptionPane.showMessageDialog(this, "Uncaught exception:\n" + ex.getMessage() + "\n\n" + sw.toString(), "Unhandled Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    // Show login dialog and set currentUserRole
    private void showLoginDialog() {
        // Create a modal dialog for login
        JDialog loginDialog = new JDialog((JFrame) null, "Login", true);
        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        loginDialog.setSize(400, 300);
        loginDialog.setLocationRelativeTo(null);

        JPanel loginPanel = new JPanel(new java.awt.GridLayout(4, 2, 10, 10));
        loginPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JButton guestButton = new JButton("Guest (No Password)");
        JButton curatorButton = new JButton("Curator");
        JButton adminButton = new JButton("Admin");

        guestButton.addActionListener(e -> {
            currentUserRole = UserRole.GUEST;
            loginDialog.dispose();
        });

        curatorButton.addActionListener(e -> {
            JPanel credPanel = new JPanel(new java.awt.GridLayout(2, 2, 10, 10));
            credPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();

            credPanel.add(new JLabel("Username:"));
            credPanel.add(usernameField);
            credPanel.add(new JLabel("Password:"));
            credPanel.add(passwordField);

            int result = JOptionPane.showConfirmDialog(loginDialog, credPanel, "Curator Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                if ("curator".equalsIgnoreCase(username) && "curator123".equals(password)) {
                    currentUserRole = UserRole.CURATOR;
                    loginDialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(loginDialog, "Invalid credentials", "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        adminButton.addActionListener(e -> {
            JPanel credPanel = new JPanel(new java.awt.GridLayout(2, 2, 10, 10));
            credPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();

            credPanel.add(new JLabel("Username:"));
            credPanel.add(usernameField);
            credPanel.add(new JLabel("Password:"));
            credPanel.add(passwordField);

            int result = JOptionPane.showConfirmDialog(loginDialog, credPanel, "Admin Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                if ("admin".equalsIgnoreCase(username) && "admin123".equals(password)) {
                    currentUserRole = UserRole.ADMIN;
                    loginDialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(loginDialog, "Invalid credentials", "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        loginPanel.add(new JLabel("Select User Type:"));
        loginPanel.add(new JLabel(""));
        loginPanel.add(guestButton);
        loginPanel.add(new JLabel(""));
        loginPanel.add(curatorButton);
        loginPanel.add(new JLabel(""));
        loginPanel.add(adminButton);
        loginPanel.add(new JLabel(""));

        loginDialog.add(loginPanel);
        loginDialog.setVisible(true);
        
        // If dialog was closed without login, exit
        if (currentUserRole == null) {
            System.exit(0);
        }
    }

    // Update button visibility based on user role
    private void updateButtonVisibility() {
        if (currentUserRole == null) return;

        boolean hasLoadPerm = currentUserRole.hasPermission("Load Tables");
        boolean hasCreatePerm = currentUserRole.hasPermission("CREATE Table");
        boolean hasInsertPerm = currentUserRole.hasPermission("INSERT Row");
        boolean hasUpdatePerm = currentUserRole.hasPermission("UPDATE Row");
        boolean hasDeletePerm = currentUserRole.hasPermission("DELETE Row");
        boolean hasFilterPerm = currentUserRole.hasPermission("FILTER");
        boolean hasAddFilterPerm = currentUserRole.hasPermission("Add Filter");
        boolean hasClearFilterPerm = currentUserRole.hasPermission("Clear Filter");

        loadButton.setEnabled(hasLoadPerm);
        createButton.setEnabled(hasCreatePerm);
        insertButton.setEnabled(hasInsertPerm);
        updateButton.setEnabled(hasUpdatePerm);
        deleteButton.setEnabled(hasDeletePerm);
        filterButton.setEnabled(hasFilterPerm);
        addFilterButton.setEnabled(hasAddFilterPerm);
        clearFilterButton.setEnabled(hasClearFilterPerm);
        logoutButton.setEnabled(true);

        statusLabel.setText("Logged in as: " + currentUserRole.getDisplayName());
    }

    // Generate a consistent pastel color for a museum id
    private java.awt.Color colorForMuseumId(int id) {
        // Use a simple deterministic HSB mapping based on id
        float hue = (float) ((id * 37) % 360) / 360f; // spread across spectrum
        float saturation = 0.35f + (float) (((id * 17) % 20)) / 100f; // 0.35 - 0.55
        float brightness = 0.92f;
        java.awt.Color base = java.awt.Color.getHSBColor(hue, saturation, brightness);
        return base;
    }

    private void onLoadClicked(ActionEvent ev) {
        loadButton.setEnabled(false);
        statusLabel.setText("Loading artist, work, and museum tables...");

        // Use SwingWorker so UI remains responsive
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                artistOriginal = queryTableModel("artist");
                workOriginal = queryTableModel("work");
                museumOriginal = queryTableModel("museum");
                museumHoursOriginal = queryTableModel("museum_hours");
                subjectOriginal = queryTableModel("subject");
                workSubjectOriginal = queryTableModel("work_subject");
                productSizeOriginal = queryTableModel("product_size");
                canvasSizeOriginal = queryTableModel("canvas_size");
                imageLinkOriginal = queryTableModel("image_link");
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    artistTable.setModel(artistOriginal);
                    workTable.setModel(workOriginal);
                    museumTable.setModel(museumOriginal);
                    museumHoursTable.setModel(museumHoursOriginal);
                    subjectTable.setModel(subjectOriginal);
                    workSubjectTable.setModel(workSubjectOriginal);
                    productSizeTable.setModel(productSizeOriginal);
                    canvasSizeTable.setModel(canvasSizeOriginal);
                    imageLinkTable.setModel(imageLinkOriginal);
                    statusLabel.setText("Loaded main and related tables.");
                    activeFilter = null;
                    filterColumnName = null;
                    activeFilterTableName = null;
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MiniViewer.this, "Error loading tables:\n" + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    loadButton.setEnabled(true);
                }
            }
        }.execute();
    }

    // Build a DefaultTableModel from a ResultSet
    private static DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();

        Vector<String> columnNames = new Vector<>(cols);
        for (int i = 1; i <= cols; i++) columnNames.add(md.getColumnName(i));

        Vector<Vector<Object>> data = new Vector<>();
        while (rs.next()) {
            Vector<Object> row = new Vector<>(cols);
            for (int i = 1; i <= cols; i++) row.add(rs.getObject(i));
            data.add(row);
        }

        return new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only
            }
        };
    }

    // Lightweight holder for column metadata from PRAGMA table_info
    private static class ColumnInfo {
        final String name;
        final String type;
        final boolean notnull;
        final boolean pk;
        final String dflt;

        ColumnInfo(String name, String type, boolean notnull, boolean pk, String dflt) {
            this.name = name;
            this.type = type == null ? "" : type;
            this.notnull = notnull;
            this.pk = pk;
            this.dflt = dflt;
        }
    }

    // Query the entire table and return a model. Uses whitelist to avoid SQL injection.
    private DefaultTableModel queryTableModel(String tableName) throws SQLException {
        if (!ALLOWED_TABLES.contains(tableName)) throw new SQLException("Table not allowed: " + tableName);

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            String sql = "SELECT * FROM " + tableName + ";";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                return buildTableModel(rs);
            }
        }
    }

    // Get the currently active table based on selected tab
    private JTable getActiveTable() {
        int tabIndex = tabbedPane.getSelectedIndex();
        switch (tabIndex) {
            case 0: return artistTable;
            case 1: return workTable;
            case 2: return museumTable;
            case 3: return museumHoursTable;
            case 4: return subjectTable;
            case 5: return workSubjectTable;
            case 6: return productSizeTable;
            case 7: return canvasSizeTable;
            case 8: return imageLinkTable;
            default: return artistTable;
        }
    }

    // Get the table name for the current tab
    private String getActiveTableName() {
        int tabIndex = tabbedPane.getSelectedIndex();
        switch (tabIndex) {
            case 0: return "artist";
            case 1: return "work";
            case 2: return "museum";
            case 3: return "museum_hours";
            case 4: return "subject";
            case 5: return "work_subject";
            case 6: return "product_size";
            case 7: return "canvas_size";
            case 8: return "image_link";
            default: return "artist";
        }
    }

    private void onCreateClicked(ActionEvent ev) {
        String sql = JOptionPane.showInputDialog(this, "Enter CREATE TABLE statement:", "");
        if (sql != null && !sql.trim().isEmpty()) {
            executeUpdate(sql, "Table created successfully.");
        }
    }

    private void onInsertClicked(ActionEvent ev) {
        String tableName = getActiveTableName();
        showInsertDialog(tableName);
    }

    private void onUpdateClicked(ActionEvent ev) {
        String tableName = getActiveTableName();
        showUpdateDialog(tableName);
    }

    private void onDeleteClicked(ActionEvent ev) {
        String tableName = getActiveTableName();
        showDeleteDialog(tableName);
    }

    // Show delete dialog: confirms deletion of the selected row by PK
    private void showDeleteDialog(String tableName) {
        JTable activeTable = getActiveTable();
        int sel = activeTable.getSelectedRow();
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "Select a row in the table first to delete.", "No Row Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            List<ColumnInfo> cols = getTableColumns(tableName);
            DefaultTableModel model = (DefaultTableModel) activeTable.getModel();
            int modelRow = activeTable.convertRowIndexToModel(sel);
            if (modelRow < 0 || modelRow >= model.getRowCount()) {
                JOptionPane.showMessageDialog(this, "Selected row is no longer available.", "Row Not Available", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Find PKs and build WHERE clause
            List<ColumnInfo> pkCols = cols.stream().filter(c -> c.pk).collect(java.util.stream.Collectors.toList());
            if (pkCols.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Table has no primary key; cannot safely delete.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Build WHERE clause with PKs
            List<String> whereCols = new ArrayList<>();
            List<Object> whereVals = new ArrayList<>();
            for (ColumnInfo pk : pkCols) {
                int idx = model.findColumn(pk.name);
                Object cur = null;
                if (idx >= 0 && idx < model.getColumnCount()) {
                    try {
                        cur = model.getValueAt(modelRow, idx);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // skip
                    }
                }
                whereCols.add(pk.name + " = ?");
                whereVals.add(cur);
            }

            String sql = "DELETE FROM " + tableName + " WHERE " + String.join(" AND ", whereCols);

            int confirm = JOptionPane.showConfirmDialog(this, "Delete row from " + tableName + "?\nThis cannot be undone.", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            // Execute
            createButton.setEnabled(false);
            deleteButton.setEnabled(false);
            statusLabel.setText("Deleting...");

            final String finalSql = sql;
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    try (Connection conn = DriverManager.getConnection(dbUrl);
                         PreparedStatement ps = conn.prepareStatement(finalSql)) {
                        int param = 1;
                        for (Object v : whereVals) {
                            if (v == null) {
                                ps.setNull(param++, Types.NULL);
                            } else {
                                ps.setObject(param++, v);
                            }
                        }
                        return ps.executeUpdate();
                    }
                }

                @Override
                protected void done() {
                    try {
                        int affected = get();
                        statusLabel.setText("Deleted " + affected + " row(s).");
                        JOptionPane.showMessageDialog(MiniViewer.this, "Deleted " + affected + " row(s).", "Success", JOptionPane.INFORMATION_MESSAGE);
                        reloadAllTables();
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                        java.io.StringWriter sw = new java.io.StringWriter();
                        ex.printStackTrace(new java.io.PrintWriter(sw));
                        System.err.println(sw.toString());
                        JOptionPane.showMessageDialog(MiniViewer.this, "Error deleting:\n" + ex.getMessage() + "\n\nStacktrace:\n" + sw.toString(), "DB Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        createButton.setEnabled(true);
                        deleteButton.setEnabled(true);
                    }
                }
            }.execute();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error reading table metadata:\n" + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void executeUpdate(String sql, String successMsg) {
        createButton.setEnabled(false);
        insertButton.setEnabled(false);
        updateButton.setEnabled(false);
        deleteButton.setEnabled(false);
        statusLabel.setText("Executing...");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                try (Connection conn = DriverManager.getConnection(dbUrl)) {
                    try (Statement stmt = conn.createStatement()) {
                        return stmt.executeUpdate(sql);
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    int rowCount = get();
                    statusLabel.setText(successMsg + " (" + rowCount + " rows affected)");
                    JOptionPane.showMessageDialog(MiniViewer.this, successMsg + "\n" + rowCount + " rows affected.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    // Reload all tables
                    reloadAllTables();
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MiniViewer.this, "Error executing statement:\n" + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    createButton.setEnabled(true);
                    insertButton.setEnabled(true);
                    updateButton.setEnabled(true);
                    deleteButton.setEnabled(true);
                }
            }
        }.execute();
    }

    // Reload all tables, preserving current filter state
    private void reloadAllTables() {
        loadButton.setEnabled(false);
        statusLabel.setText("Reloading...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                artistOriginal = queryTableModel("artist");
                workOriginal = queryTableModel("work");
                museumOriginal = queryTableModel("museum");
                museumHoursOriginal = queryTableModel("museum_hours");
                subjectOriginal = queryTableModel("subject");
                workSubjectOriginal = queryTableModel("work_subject");
                productSizeOriginal = queryTableModel("product_size");
                canvasSizeOriginal = queryTableModel("canvas_size");
                imageLinkOriginal = queryTableModel("image_link");
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    // Reapply filter if active
                    if (activeFilter != null && filterColumnName != null && activeFilterTableName != null) {
                        applyFilterToAllTables(activeFilterTableName, filterColumnName, "equals", activeFilter);
                    } else {
                        artistTable.setModel(artistOriginal);
                        workTable.setModel(workOriginal);
                        museumTable.setModel(museumOriginal);
                        museumHoursTable.setModel(museumHoursOriginal);
                        subjectTable.setModel(subjectOriginal);
                        workSubjectTable.setModel(workSubjectOriginal);
                        productSizeTable.setModel(productSizeOriginal);
                        canvasSizeTable.setModel(canvasSizeOriginal);
                        imageLinkTable.setModel(imageLinkOriginal);
                        statusLabel.setText("Reloaded all tables.");
                    }
                } catch (Exception ex) {
                    statusLabel.setText("Error reloading: " + ex.getMessage());
                } finally {
                    loadButton.setEnabled(true);
                }
            }
        }.execute();
    }

    // Retrieve column metadata for a table using PRAGMA table_info
    private List<ColumnInfo> getTableColumns(String tableName) throws SQLException {
        List<ColumnInfo> cols = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            String pragma = "PRAGMA table_info('" + tableName + "')";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(pragma)) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String type = rs.getString("type");
                    boolean notnull = rs.getInt("notnull") != 0;
                    boolean pk = rs.getInt("pk") != 0;
                    String dflt = rs.getString("dflt_value");
                    cols.add(new ColumnInfo(name, type, notnull, pk, dflt));
                }
            }
        }
        return cols;
    }

    // Show a simple insert dialog built from the table's columns and run a PreparedStatement
    private void showInsertDialog(String tableName) {
        try {
            List<ColumnInfo> cols = getTableColumns(tableName);
            JPanel panel = new JPanel(new java.awt.GridLayout(cols.size(), 2, 4, 4));
            List<JTextField> fields = new ArrayList<>();
            for (ColumnInfo c : cols) {
                JLabel lbl = new JLabel(c.name + (c.pk ? " (PK)" : "") + ":");
                JTextField tf = new JTextField();
                if (c.dflt != null) tf.setText(c.dflt);
                panel.add(lbl);
                panel.add(tf);
                fields.add(tf);
            }

            int res = JOptionPane.showConfirmDialog(this, panel, "Insert into " + tableName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return;

            // Build INSERT for columns where user provided a value (non-empty), otherwise let DB default
            List<String> names = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                String v = fields.get(i).getText().trim();
                if (!v.isEmpty()) {
                    names.add(cols.get(i).name);
                    placeholders.add("?");
                    values.add(v);
                }
            }
            if (names.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No values provided; aborting insert.", "Abort", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String sql = "INSERT INTO " + tableName + " (" + String.join(",", names) + ") VALUES (" + String.join(",", placeholders) + ")";

            // Execute with PreparedStatement
            createButton.setEnabled(false);
            insertButton.setEnabled(false);
            updateButton.setEnabled(false);
            deleteButton.setEnabled(false);
            statusLabel.setText("Inserting...");

            final String finalSql = sql;
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    try (Connection conn = DriverManager.getConnection(dbUrl);
                         PreparedStatement ps = conn.prepareStatement(finalSql)) {
                        for (int i = 0; i < values.size(); i++) {
                            String raw = (String) values.get(i);
                            // Try integer, then double, else string
                            try { ps.setInt(i+1, Integer.parseInt(raw)); continue; } catch (Exception ignored) {}
                            try { ps.setDouble(i+1, Double.parseDouble(raw)); continue; } catch (Exception ignored) {}
                            ps.setString(i+1, raw);
                        }
                        return ps.executeUpdate();
                    }
                }

                @Override
                protected void done() {
                    try {
                        int affected = get();
                        statusLabel.setText("Inserted " + affected + " row(s).");
                        JOptionPane.showMessageDialog(MiniViewer.this, "Inserted " + affected + " row(s).", "Success", JOptionPane.INFORMATION_MESSAGE);
                        reloadAllTables();
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                        JOptionPane.showMessageDialog(MiniViewer.this, "Error inserting:\n" + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        createButton.setEnabled(true);
                        insertButton.setEnabled(true);
                        updateButton.setEnabled(true);
                        deleteButton.setEnabled(true);
                    }
                }
            }.execute();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error reading table metadata:\n" + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Show update dialog: requires a selected row in the JTable; uses PK columns to build WHERE
    private void showUpdateDialog(String tableName) {
        JTable activeTable = getActiveTable();
        int sel = activeTable.getSelectedRow();
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "Select a row in the table first to update.", "No Row Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            List<ColumnInfo> cols = getTableColumns(tableName);
            DefaultTableModel model = (DefaultTableModel) activeTable.getModel();
            int modelRow = activeTable.convertRowIndexToModel(sel);
            if (modelRow < 0 || modelRow >= model.getRowCount()) {
                JOptionPane.showMessageDialog(this, "Selected row is no longer available. Please re-select the row after loading the table.", "Row Not Available", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JPanel panel = new JPanel(new java.awt.GridLayout(cols.size(), 2));
            List<JTextField> fields = new ArrayList<>();
            List<ColumnInfo> pkCols = new ArrayList<>();
            for (ColumnInfo c : cols) {
                JLabel lbl = new JLabel(c.name + (c.pk ? " (PK)" : "") + ":");
                JTextField tf = new JTextField();
                int colIndex = model.findColumn(c.name);
                if (colIndex >= 0 && colIndex < model.getColumnCount()) {
                    try {
                        Object cur = model.getValueAt(modelRow, colIndex);
                        tf.setText(cur == null ? "" : cur.toString());
                    } catch (ArrayIndexOutOfBoundsException e) {
                        String msg = String.format("Debug: getValueAt failed (modelRow=%d, colIndex=%d, rows=%d, cols=%d)", modelRow, colIndex, model.getRowCount(), model.getColumnCount());
                        JOptionPane.showMessageDialog(this, msg + "\n" + e.toString(), "Debug", JOptionPane.ERROR_MESSAGE);
                    }
                }
                panel.add(lbl);
                panel.add(tf);
                fields.add(tf);
                if (c.pk) pkCols.add(c);
            }

            if (pkCols.isEmpty()) {
                int opt = JOptionPane.showConfirmDialog(this, "Table has no primary key. We'll use all columns' current values to locate the row. Continue?", "No PK", JOptionPane.YES_NO_OPTION);
                if (opt != JOptionPane.YES_OPTION) return;
            }

            int res = JOptionPane.showConfirmDialog(this, panel, "Update row in " + tableName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return;

            // Build SET clauses for columns that changed (or all non-PK columns)
            List<String> setCols = new ArrayList<>();
            List<Object> setVals = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                ColumnInfo c = cols.get(i);
                String newVal = "";
                if (i < fields.size()) {
                    newVal = fields.get(i).getText().trim();
                }
                int colIndex = model.findColumn(c.name);
                String curVal = "";
                if (colIndex >= 0 && colIndex < model.getColumnCount()) {
                    try {
                        Object cur = model.getValueAt(modelRow, colIndex);
                        curVal = cur == null ? "" : cur.toString();
                    } catch (ArrayIndexOutOfBoundsException e) {
                        String msg = String.format("Debug: getValueAt failed (modelRow=%d, colIndex=%d, rows=%d, cols=%d)", modelRow, colIndex, model.getRowCount(), model.getColumnCount());
                        JOptionPane.showMessageDialog(this, msg + "\n" + e.toString(), "Debug", JOptionPane.ERROR_MESSAGE);
                    }
                }
                if (!c.pk) {
                    // If value changed or user entered something, include in SET
                    if (!newVal.equals(curVal)) {
                        setCols.add(c.name + " = ?");
                        setVals.add(newVal);
                    }
                }
            }

            if (setCols.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No changes detected; aborting update.", "No Changes", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // WHERE clause: use PKs if present, else use equality on all columns' original values
            List<String> whereCols = new ArrayList<>();
            List<Object> whereVals = new ArrayList<>();
            if (!pkCols.isEmpty()) {
                for (ColumnInfo pk : pkCols) {
                    int idx = model.findColumn(pk.name);
                    Object cur = null;
                    if (idx >= 0 && idx < model.getColumnCount()) {
                        try {
                            cur = model.getValueAt(modelRow, idx);
                        } catch (ArrayIndexOutOfBoundsException e) {
                            String msg = String.format("Debug: getValueAt failed for PK (modelRow=%d, idx=%d, rows=%d, cols=%d)", modelRow, idx, model.getRowCount(), model.getColumnCount());
                            JOptionPane.showMessageDialog(this, msg + "\n" + e.toString(), "Debug", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                    whereCols.add(pk.name + " = ?");
                    whereVals.add(cur);
                }
            } else {
                // fallback: use all columns' original values
                for (int i = 0; i < cols.size(); i++) {
                    ColumnInfo c = cols.get(i);
                    int idx = model.findColumn(c.name);
                    Object cur = null;
                    if (idx >= 0 && idx < model.getColumnCount()) {
                        try {
                            cur = model.getValueAt(modelRow, idx);
                        } catch (ArrayIndexOutOfBoundsException e) {
                            String msg = String.format("Debug: getValueAt failed in fallback (modelRow=%d, idx=%d, rows=%d, cols=%d)", modelRow, idx, model.getRowCount(), model.getColumnCount());
                            JOptionPane.showMessageDialog(this, msg + "\n" + e.toString(), "Debug", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                    whereCols.add(c.name + " IS ?");
                    whereVals.add(cur);
                }
            }

            String sql = "UPDATE " + tableName + " SET " + String.join(",", setCols) + " WHERE " + String.join(" AND ", whereCols);

            // Execute
            createButton.setEnabled(false);
            insertButton.setEnabled(false);
            updateButton.setEnabled(false);
            deleteButton.setEnabled(false);
            statusLabel.setText("Updating...");

            final String finalSql = sql;
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    // Diagnostic: count parameter placeholders and compare with supplied values
                    int expectedParams = 0;
                    for (int i = 0; i < finalSql.length(); i++) if (finalSql.charAt(i) == '?') expectedParams++;
                    int provided = setVals.size() + whereVals.size();
                    if (expectedParams != provided) {
                        throw new SQLException("Parameter count mismatch: SQL has " + expectedParams + " placeholders but provided " + provided + " values. SQL=" + finalSql + "");
                    }
                    try (Connection conn = DriverManager.getConnection(dbUrl);
                         PreparedStatement ps = conn.prepareStatement(finalSql)) {
                        int param = 1;
                        // set SET values
                        for (Object v : setVals) {
                            if (v == null || (v instanceof String && ((String) v).isEmpty())) {
                                ps.setNull(param++, Types.NULL);
                            } else {
                                ps.setObject(param++, v);
                            }
                        }
                        // set WHERE values
                        for (Object v : whereVals) {
                            if (v == null) {
                                ps.setNull(param++, Types.NULL);
                            } else {
                                ps.setObject(param++, v);
                            }
                        }
                        return ps.executeUpdate();
                    }
                }

                @Override
                protected void done() {
                    try {
                        int affected = get();
                        statusLabel.setText("Updated " + affected + " row(s).");
                        JOptionPane.showMessageDialog(MiniViewer.this, "Updated " + affected + " row(s).", "Success", JOptionPane.INFORMATION_MESSAGE);
                        reloadAllTables();
                    } catch (Exception ex) {
                            statusLabel.setText("Error: " + ex.getMessage());
                            // Show full stacktrace for debugging
                            java.io.StringWriter sw = new java.io.StringWriter();
                            ex.printStackTrace(new java.io.PrintWriter(sw));
                            System.err.println(sw.toString());
                            JOptionPane.showMessageDialog(MiniViewer.this, "Error updating:\n" + ex.getMessage() + "\n\nStacktrace:\n" + sw.toString(), "DB Error", JOptionPane.ERROR_MESSAGE);
                        } finally {
                        createButton.setEnabled(true);
                        insertButton.setEnabled(true);
                        updateButton.setEnabled(true);
                        deleteButton.setEnabled(true);
                    }
                }
            }.execute();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error reading table metadata:\n" + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onFilterClicked(ActionEvent ev) {
        int activeTab = tabbedPane.getSelectedIndex();
        DefaultTableModel activeModel = null;
        String activeTableName = null;
        switch (activeTab) {
            case 0: activeModel = artistOriginal; activeTableName = "artist"; break;
            case 1: activeModel = workOriginal; activeTableName = "work"; break;
            case 2: activeModel = museumOriginal; activeTableName = "museum"; break;
            case 3: activeModel = museumHoursOriginal; activeTableName = "museum_hours"; break;
            case 4: activeModel = subjectOriginal; activeTableName = "subject"; break;
            case 5: activeModel = workSubjectOriginal; activeTableName = "work_subject"; break;
            case 6: activeModel = productSizeOriginal; activeTableName = "product_size"; break;
            case 7: activeModel = canvasSizeOriginal; activeTableName = "canvas_size"; break;
            case 8: activeModel = imageLinkOriginal; activeTableName = "image_link"; break;
        }

        if (activeModel == null || activeModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Load tables first.", "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get column names from active table
        List<String> colNames = new ArrayList<>();
        for (int i = 0; i < activeModel.getColumnCount(); i++) {
            colNames.add(activeModel.getColumnName(i));
        }

        // Build filter dialog
        JPanel panel = new JPanel(new java.awt.GridLayout(3, 1, 4, 4));
        JComboBox<String> colCombo = new JComboBox<>(colNames.toArray(new String[0]));
        JComboBox<String> opCombo = new JComboBox<>(new String[]{"contains", "equals", "starts with", "ends with"});
        JTextField valueField = new JTextField();

        panel.add(new JLabel("Column:"));
        panel.add(colCombo);
        panel.add(new JLabel("Operation:"));
        panel.add(opCombo);
        panel.add(new JLabel("Value:"));
        panel.add(valueField);

        int res = JOptionPane.showConfirmDialog(this, panel, "Filter by " + activeTableName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String colName = (String) colCombo.getSelectedItem();
        String op = (String) opCombo.getSelectedItem();
        String value = valueField.getText();

        if (value.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a filter value.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        filterColumnName = colName;
        activeFilter = value;
        activeFilterTableName = activeTableName;
        applyFilterToAllTables(activeTableName, colName, op, value);
    }

    // Apply cascading filter from any source table
    private void applyFilterToAllTables(String sourceTableName, String colName, String op, String value) {
        final String filterValue = value.toLowerCase();

        // Create filtered models for all tables
        DefaultTableModel filteredArtist = createEmptyModel(artistOriginal);
        DefaultTableModel filteredWork = createEmptyModel(workOriginal);
        DefaultTableModel filteredMuseum = createEmptyModel(museumOriginal);
        DefaultTableModel filteredWorkSubject = createEmptyModel(workSubjectOriginal);
        DefaultTableModel filteredSubject = createEmptyModel(subjectOriginal);
        DefaultTableModel filteredImageLink = createEmptyModel(imageLinkOriginal);
        DefaultTableModel filteredProductSize = createEmptyModel(productSizeOriginal);
        DefaultTableModel filteredCanvas = createEmptyModel(canvasSizeOriginal);
        DefaultTableModel filteredMuseumHours = createEmptyModel(museumHoursOriginal);

        List<Integer> matchedArtistIds = new ArrayList<>();
        List<Integer> matchedWorkIds = new ArrayList<>();
        java.util.Set<Integer> matchedMuseumIds = new java.util.HashSet<>();
        java.util.Set<Integer> matchedSubjectIds = new java.util.HashSet<>();
        java.util.Set<Integer> matchedCanvasIds = new java.util.HashSet<>();

        // Filter based on source table
        if ("artist".equals(sourceTableName)) {
            // Filter artist table by column value
            int colIndex = artistOriginal.findColumn(colName);
            for (int i = 0; i < artistOriginal.getRowCount(); i++) {
                Object cellVal = artistOriginal.getValueAt(i, colIndex);
                String cellStr = (cellVal == null ? "" : cellVal.toString()).toLowerCase();
                if (matchesFilter(cellStr, filterValue, op)) {
                    addRowToModel(filteredArtist, artistOriginal, i);
                    Integer aid = (Integer) artistOriginal.getValueAt(i, 0);
                    matchedArtistIds.add(aid);
                }
            }

            // Cascade: filter work by matched artists
            filterWorkByArtistIds(filteredWork, matchedWorkIds, matchedArtistIds);

            // Cascade: filter other tables by matched work ids
            cascadeFromWork(filteredWorkSubject, filteredSubject, filteredImageLink, 
                          filteredProductSize, filteredCanvas, filteredMuseum, filteredMuseumHours,
                          matchedWorkIds, matchedSubjectIds, matchedCanvasIds, matchedMuseumIds);

        } else if ("work".equals(sourceTableName)) {
            // Filter work table by column value
            int colIndex = workOriginal.findColumn(colName);
            for (int i = 0; i < workOriginal.getRowCount(); i++) {
                Object cellVal = workOriginal.getValueAt(i, colIndex);
                String cellStr = (cellVal == null ? "" : cellVal.toString()).toLowerCase();
                if (matchesFilter(cellStr, filterValue, op)) {
                    addRowToModel(filteredWork, workOriginal, i);
                    Integer wid = (Integer) workOriginal.getValueAt(i, 0);
                    matchedWorkIds.add(wid);
                    
                    // Also add the artist
                    int artistIdCol = workOriginal.findColumn("artist_id");
                    if (artistIdCol >= 0) {
                        Integer aid = (Integer) workOriginal.getValueAt(i, artistIdCol);
                        matchedArtistIds.add(aid);
                    }
                }
            }
            // Filter artists
            filterArtistsByIds(filteredArtist, matchedArtistIds);

            // Cascade
            cascadeFromWork(filteredWorkSubject, filteredSubject, filteredImageLink, 
                          filteredProductSize, filteredCanvas, filteredMuseum, filteredMuseumHours,
                          matchedWorkIds, matchedSubjectIds, matchedCanvasIds, matchedMuseumIds);

        } else if ("museum".equals(sourceTableName)) {
            // Filter museum by column value
            int colIndex = museumOriginal.findColumn(colName);
            for (int i = 0; i < museumOriginal.getRowCount(); i++) {
                Object cellVal = museumOriginal.getValueAt(i, colIndex);
                String cellStr = (cellVal == null ? "" : cellVal.toString()).toLowerCase();
                if (matchesFilter(cellStr, filterValue, op)) {
                    addRowToModel(filteredMuseum, museumOriginal, i);
                    Integer mid = (Integer) museumOriginal.getValueAt(i, 0);
                    matchedMuseumIds.add(mid);
                }
            }
            // Filter work by museum, then cascade
            filterWorkByMuseumIds(filteredWork, matchedWorkIds, matchedArtistIds, matchedMuseumIds);
            filterArtistsByIds(filteredArtist, matchedArtistIds);
            cascadeFromWork(filteredWorkSubject, filteredSubject, filteredImageLink, 
                          filteredProductSize, filteredCanvas, filteredMuseum, filteredMuseumHours,
                          matchedWorkIds, matchedSubjectIds, matchedCanvasIds, matchedMuseumIds);

        } else if ("subject".equals(sourceTableName)) {
            // Filter subject by column value
            int colIndex = subjectOriginal.findColumn(colName);
            for (int i = 0; i < subjectOriginal.getRowCount(); i++) {
                Object cellVal = subjectOriginal.getValueAt(i, colIndex);
                String cellStr = (cellVal == null ? "" : cellVal.toString()).toLowerCase();
                if (matchesFilter(cellStr, filterValue, op)) {
                    addRowToModel(filteredSubject, subjectOriginal, i);
                    Integer sid = (Integer) subjectOriginal.getValueAt(i, 0);
                    matchedSubjectIds.add(sid);
                }
            }
            // Filter work_subject by matched subjects
            filterWorkSubjectBySubjectIds(filteredWorkSubject, matchedWorkIds, matchedSubjectIds);
            // Then cascade from work
            filterArtistsByIds(filteredArtist, matchedArtistIds);
            filterWorkByIds(filteredWork, matchedWorkIds, matchedArtistIds, matchedMuseumIds);
            cascadeFromWork(filteredWorkSubject, filteredSubject, filteredImageLink, 
                          filteredProductSize, filteredCanvas, filteredMuseum, filteredMuseumHours,
                          matchedWorkIds, matchedSubjectIds, matchedCanvasIds, matchedMuseumIds);

        } else {
            // Default: show all
            addAllRows(filteredArtist, artistOriginal);
            addAllRows(filteredWork, workOriginal);
            addAllRows(filteredMuseum, museumOriginal);
            addAllRows(filteredWorkSubject, workSubjectOriginal);
            addAllRows(filteredSubject, subjectOriginal);
            addAllRows(filteredImageLink, imageLinkOriginal);
            addAllRows(filteredProductSize, productSizeOriginal);
            addAllRows(filteredCanvas, canvasSizeOriginal);
            addAllRows(filteredMuseumHours, museumHoursOriginal);
        }

        // Assign unique colors for matched museums
        museumColorMap.clear();
        for (Integer mid : matchedMuseumIds) {
            if (mid != null) museumColorMap.put(mid, colorForMuseumId(mid));
        }

        // Update all tables
        artistTable.setModel(filteredArtist);
        workTable.setModel(filteredWork);
        museumTable.setModel(filteredMuseum);
        workSubjectTable.setModel(filteredWorkSubject);
        subjectTable.setModel(filteredSubject);
        imageLinkTable.setModel(filteredImageLink);
        productSizeTable.setModel(filteredProductSize);
        canvasSizeTable.setModel(filteredCanvas);
        museumHoursTable.setModel(filteredMuseumHours);

        // Apply cell/row renderers to color-code museum relationships
        applyRenderers();

        statusLabel.setText("Filtered by " + sourceTableName + "." + colName);
    }

    // Helper: create empty model with columns from original
    private DefaultTableModel createEmptyModel(DefaultTableModel original) {
        DefaultTableModel dm = new DefaultTableModel() {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        if (original != null) {
            for (int i = 0; i < original.getColumnCount(); i++) {
                dm.addColumn(original.getColumnName(i));
            }
        }
        return dm;
    }

    // Helper: add a row from source to destination model
    private void addRowToModel(DefaultTableModel dest, DefaultTableModel source, int rowIndex) {
        Object[] row = new Object[source.getColumnCount()];
        for (int c = 0; c < source.getColumnCount(); c++) {
            row[c] = source.getValueAt(rowIndex, c);
        }
        dest.addRow(row);
    }

    // Helper: add all rows from source to destination
    private void addAllRows(DefaultTableModel dest, DefaultTableModel source) {
        if (source == null) return;
        for (int i = 0; i < source.getRowCount(); i++) {
            addRowToModel(dest, source, i);
        }
    }

    // Helper: check if a string matches filter criteria
    private boolean matchesFilter(String cellStr, String filterValue, String op) {
        if ("contains".equals(op)) {
            return cellStr.contains(filterValue);
        } else if ("equals".equals(op)) {
            return cellStr.equals(filterValue);
        } else if ("starts with".equals(op)) {
            return cellStr.startsWith(filterValue);
        } else if ("ends with".equals(op)) {
            return cellStr.endsWith(filterValue);
        }
        return false;
    }

    // Helper: filter work by matched artist ids
    private void filterWorkByArtistIds(DefaultTableModel dest, List<Integer> matchedWorkIds, List<Integer> matchedArtistIds) {
        if (workOriginal == null) return;
        int artistIdCol = workOriginal.findColumn("artist_id");
        for (int i = 0; i < workOriginal.getRowCount(); i++) {
            if (artistIdCol >= 0) {
                Integer aid = (Integer) workOriginal.getValueAt(i, artistIdCol);
                if (matchedArtistIds.contains(aid)) {
                    addRowToModel(dest, workOriginal, i);
                    Integer wid = (Integer) workOriginal.getValueAt(i, 0);
                    matchedWorkIds.add(wid);
                }
            }
        }
    }

    // Helper: filter work by museum ids
    private void filterWorkByMuseumIds(DefaultTableModel dest, List<Integer> matchedWorkIds, List<Integer> matchedArtistIds, java.util.Set<Integer> matchedMuseumIds) {
        if (workOriginal == null) return;
        int workMuseumCol = -1;
        for (int c = 0; c < workOriginal.getColumnCount(); c++) {
            String nm = workOriginal.getColumnName(c);
            if (nm != null && nm.toLowerCase().equals("museum_id")) {
                workMuseumCol = c;
                break;
            }
        }
        for (int i = 0; i < workOriginal.getRowCount(); i++) {
            boolean include = false;
            if (workMuseumCol >= 0) {
                Object mv = workOriginal.getValueAt(i, workMuseumCol);
                Integer mid = null;
                if (mv instanceof Number) mid = ((Number) mv).intValue();
                else if (mv != null) { try { mid = Integer.parseInt(mv.toString()); } catch (Exception ignored) {} }
                if (mid != null && matchedMuseumIds.contains(mid)) include = true;
            }
            if (include) {
                addRowToModel(dest, workOriginal, i);
                Integer wid = (Integer) workOriginal.getValueAt(i, 0);
                matchedWorkIds.add(wid);
                Integer aid = (Integer) workOriginal.getValueAt(i, workOriginal.findColumn("artist_id"));
                if (aid != null) matchedArtistIds.add(aid);
            }
        }
    }

    // Helper: filter work by ids
    private void filterWorkByIds(DefaultTableModel dest, List<Integer> matchedWorkIds, List<Integer> matchedArtistIds, java.util.Set<Integer> matchedMuseumIds) {
        if (workOriginal == null || matchedWorkIds.isEmpty()) {
            addAllRows(dest, workOriginal);
            if (workOriginal != null) {
                int idCol = workOriginal.findColumn("artist_id");
                for (int i = 0; i < workOriginal.getRowCount(); i++) {
                    Integer aid = (Integer) workOriginal.getValueAt(i, idCol);
                    if (aid != null) matchedArtistIds.add(aid);
                }
            }
            return;
        }
        int idCol = workOriginal.findColumn("id");
        int artistIdCol = workOriginal.findColumn("artist_id");
        for (int i = 0; i < workOriginal.getRowCount(); i++) {
            Object idv = workOriginal.getValueAt(i, idCol);
            Integer id = null;
            if (idv instanceof Number) id = ((Number) idv).intValue();
            if (id != null && matchedWorkIds.contains(id)) {
                addRowToModel(dest, workOriginal, i);
                if (artistIdCol >= 0) {
                    Integer aid = (Integer) workOriginal.getValueAt(i, artistIdCol);
                    if (aid != null) matchedArtistIds.add(aid);
                }
            }
        }
    }

    // Helper: filter artists by ids
    private void filterArtistsByIds(DefaultTableModel dest, List<Integer> matchedArtistIds) {
        if (artistOriginal == null) return;
        if (matchedArtistIds.isEmpty()) {
            addAllRows(dest, artistOriginal);
            return;
        }
        int idCol = artistOriginal.findColumn("id");
        for (int i = 0; i < artistOriginal.getRowCount(); i++) {
            Object idv = artistOriginal.getValueAt(i, idCol);
            Integer id = null;
            if (idv instanceof Number) id = ((Number) idv).intValue();
            if (id != null && matchedArtistIds.contains(id)) {
                addRowToModel(dest, artistOriginal, i);
            }
        }
    }

    // Helper: filter work_subject by subject ids
    private void filterWorkSubjectBySubjectIds(DefaultTableModel dest, List<Integer> matchedWorkIds, java.util.Set<Integer> matchedSubjectIds) {
        if (workSubjectOriginal == null) return;
        int wsSubjectCol = workSubjectOriginal.findColumn("subject_id");
        int wsWorkCol = workSubjectOriginal.findColumn("work_id");
        for (int i = 0; i < workSubjectOriginal.getRowCount(); i++) {
            if (wsSubjectCol >= 0) {
                Object sv = workSubjectOriginal.getValueAt(i, wsSubjectCol);
                Integer sid = null;
                if (sv instanceof Number) sid = ((Number) sv).intValue();
                if (sid != null && matchedSubjectIds.contains(sid)) {
                    addRowToModel(dest, workSubjectOriginal, i);
                    if (wsWorkCol >= 0) {
                        Object wv = workSubjectOriginal.getValueAt(i, wsWorkCol);
                        Integer wid = null;
                        if (wv instanceof Number) wid = ((Number) wv).intValue();
                        if (wid != null) matchedWorkIds.add(wid);
                    }
                }
            }
        }
    }

    // Helper: cascade filter from matched work ids
    private void cascadeFromWork(DefaultTableModel fws, DefaultTableModel fs, DefaultTableModel fil, DefaultTableModel fps, DefaultTableModel fc, DefaultTableModel fm, DefaultTableModel fmh, 
                                  List<Integer> matchedWorkIds, java.util.Set<Integer> matchedSubjectIds, java.util.Set<Integer> matchedCanvasIds, java.util.Set<Integer> matchedMuseumIds) {
        if (matchedWorkIds.isEmpty()) {
            addAllRows(fws, workSubjectOriginal);
            addAllRows(fs, subjectOriginal);
            addAllRows(fil, imageLinkOriginal);
            addAllRows(fps, productSizeOriginal);
            addAllRows(fc, canvasSizeOriginal);
            addAllRows(fm, museumOriginal);
            addAllRows(fmh, museumHoursOriginal);
            return;
        }

        // Filter work_subject by matched work ids
        int wsWorkCol = workSubjectOriginal == null ? -1 : workSubjectOriginal.findColumn("work_id");
        int wsSubjectCol = workSubjectOriginal == null ? -1 : workSubjectOriginal.findColumn("subject_id");
        if (workSubjectOriginal != null && wsWorkCol >= 0) {
            for (int i = 0; i < workSubjectOriginal.getRowCount(); i++) {
                Object wv = workSubjectOriginal.getValueAt(i, wsWorkCol);
                Integer wid = null;
                if (wv instanceof Number) wid = ((Number) wv).intValue();
                if (wid != null && matchedWorkIds.contains(wid)) {
                    addRowToModel(fws, workSubjectOriginal, i);
                    if (wsSubjectCol >= 0) {
                        Object sv = workSubjectOriginal.getValueAt(i, wsSubjectCol);
                        Integer sid = null;
                        if (sv instanceof Number) sid = ((Number) sv).intValue();
                        if (sid != null) matchedSubjectIds.add(sid);
                    }
                }
            }
        }

        // Filter subject by matched ids
        if (subjectOriginal != null && !matchedSubjectIds.isEmpty()) {
            int subjIdCol = subjectOriginal.findColumn("id");
            for (int i = 0; i < subjectOriginal.getRowCount(); i++) {
                Object idv = subjectOriginal.getValueAt(i, subjIdCol);
                Integer sid = null;
                if (idv instanceof Number) sid = ((Number) idv).intValue();
                if (sid != null && matchedSubjectIds.contains(sid)) {
                    addRowToModel(fs, subjectOriginal, i);
                }
            }
        }

        // Filter image_link by matched work ids
        if (imageLinkOriginal != null) {
            int ilWorkCol = imageLinkOriginal.findColumn("work_id");
            for (int i = 0; i < imageLinkOriginal.getRowCount(); i++) {
                if (ilWorkCol >= 0) {
                    Object wv = imageLinkOriginal.getValueAt(i, ilWorkCol);
                    Integer wid = null;
                    if (wv instanceof Number) wid = ((Number) wv).intValue();
                    if (wid != null && matchedWorkIds.contains(wid)) {
                        addRowToModel(fil, imageLinkOriginal, i);
                    }
                }
            }
        }

        // Filter product_size and collect canvas ids
        if (productSizeOriginal != null) {
            int psWorkCol = productSizeOriginal.findColumn("work_id");
            int psCanvasCol = productSizeOriginal.findColumn("canvas_size_id");
            for (int i = 0; i < productSizeOriginal.getRowCount(); i++) {
                if (psWorkCol >= 0) {
                    Object wv = productSizeOriginal.getValueAt(i, psWorkCol);
                    Integer wid = null;
                    if (wv instanceof Number) wid = ((Number) wv).intValue();
                    if (wid != null && matchedWorkIds.contains(wid)) {
                        addRowToModel(fps, productSizeOriginal, i);
                        if (psCanvasCol >= 0) {
                            Object cv = productSizeOriginal.getValueAt(i, psCanvasCol);
                            Integer cid = null;
                            if (cv instanceof Number) cid = ((Number) cv).intValue();
                            if (cid != null) matchedCanvasIds.add(cid);
                        }
                    }
                }
            }
        }

        // Filter canvas_size by matched ids
        if (canvasSizeOriginal != null) {
            if (!matchedCanvasIds.isEmpty()) {
                int csIdCol = canvasSizeOriginal.findColumn("id");
                for (int i = 0; i < canvasSizeOriginal.getRowCount(); i++) {
                    Object idv = canvasSizeOriginal.getValueAt(i, csIdCol);
                    Integer cid = null;
                    if (idv instanceof Number) cid = ((Number) idv).intValue();
                    if (cid != null && matchedCanvasIds.contains(cid)) {
                        addRowToModel(fc, canvasSizeOriginal, i);
                    }
                }
            } else {
                addAllRows(fc, canvasSizeOriginal);
            }
        }

        // Filter museum by work museum_id
        if (museumOriginal != null) {
            int workMuseumCol = -1;
            for (int c = 0; c < workOriginal.getColumnCount(); c++) {
                String nm = workOriginal.getColumnName(c);
                if (nm != null && nm.toLowerCase().equals("museum_id")) {
                    workMuseumCol = c;
                    break;
                }
            }
            if (workMuseumCol >= 0) {
                for (int i = 0; i < workOriginal.getRowCount(); i++) {
                    Integer wid = (Integer) workOriginal.getValueAt(i, 0);
                    if (matchedWorkIds.contains(wid)) {
                        Object mv = workOriginal.getValueAt(i, workMuseumCol);
                        Integer mid = null;
                        if (mv instanceof Number) mid = ((Number) mv).intValue();
                        if (mid != null) matchedMuseumIds.add(mid);
                    }
                }
                int museumIdCol = museumOriginal.findColumn("id");
                for (int i = 0; i < museumOriginal.getRowCount(); i++) {
                    Object idv = museumOriginal.getValueAt(i, museumIdCol);
                    Integer mid = null;
                    if (idv instanceof Number) mid = ((Number) idv).intValue();
                    if (mid != null && matchedMuseumIds.contains(mid)) {
                        addRowToModel(fm, museumOriginal, i);
                    }
                }
            } else {
                addAllRows(fm, museumOriginal);
            }
        }

        // Filter museum_hours by matched museum ids
        if (museumHoursOriginal != null) {
            if (!matchedMuseumIds.isEmpty()) {
                int mhMuseumCol = museumHoursOriginal.findColumn("museum_id");
                for (int i = 0; i < museumHoursOriginal.getRowCount(); i++) {
                    if (mhMuseumCol >= 0) {
                        Object mv = museumHoursOriginal.getValueAt(i, mhMuseumCol);
                        Integer mid = null;
                        if (mv instanceof Number) mid = ((Number) mv).intValue();
                        if (mid != null && matchedMuseumIds.contains(mid)) {
                            addRowToModel(fmh, museumHoursOriginal, i);
                        }
                    }
                }
            } else {
                addAllRows(fmh, museumHoursOriginal);
            }
        }
    }

    private void onClearFilterClicked(ActionEvent ev) {
        if (activeFilter != null) {
            artistTable.setModel(artistOriginal);
            workTable.setModel(workOriginal);
            museumTable.setModel(museumOriginal);
            activeFilter = null;
            filterColumnName = null;
            activeFilterTableName = null;
            museumColorMap.clear();
            applyRenderers();
            statusLabel.setText("Filter cleared. Showing all data.");
        } else {
            JOptionPane.showMessageDialog(this, "No filter is currently active.", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // Add another filter on top of existing filtered results
    private void onAddFilterClicked(ActionEvent ev) {
        // Get current active model (filtered view)
        int activeTab = tabbedPane.getSelectedIndex();
        DefaultTableModel activeModel = null;
        String activeTableName = null;
        switch (activeTab) {
            case 0: activeModel = (DefaultTableModel) artistTable.getModel(); activeTableName = "artist"; break;
            case 1: activeModel = (DefaultTableModel) workTable.getModel(); activeTableName = "work"; break;
            case 2: activeModel = (DefaultTableModel) museumTable.getModel(); activeTableName = "museum"; break;
            case 3: activeModel = (DefaultTableModel) museumHoursTable.getModel(); activeTableName = "museum_hours"; break;
            case 4: activeModel = (DefaultTableModel) subjectTable.getModel(); activeTableName = "subject"; break;
            case 5: activeModel = (DefaultTableModel) workSubjectTable.getModel(); activeTableName = "work_subject"; break;
            case 6: activeModel = (DefaultTableModel) productSizeTable.getModel(); activeTableName = "product_size"; break;
            case 7: activeModel = (DefaultTableModel) canvasSizeTable.getModel(); activeTableName = "canvas_size"; break;
            case 8: activeModel = (DefaultTableModel) imageLinkTable.getModel(); activeTableName = "image_link"; break;
        }

        if (activeModel == null || activeModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No data to filter. Apply a filter first.", "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Get column names from current filtered table
        List<String> colNames = new ArrayList<>();
        for (int i = 0; i < activeModel.getColumnCount(); i++) {
            colNames.add(activeModel.getColumnName(i));
        }

        // Build filter dialog
        JPanel panel = new JPanel(new java.awt.GridLayout(3, 1, 4, 4));
        JComboBox<String> colCombo = new JComboBox<>(colNames.toArray(new String[0]));
        JComboBox<String> opCombo = new JComboBox<>(new String[]{"contains", "equals", "starts with", "ends with"});
        JTextField valueField = new JTextField();

        panel.add(new JLabel("Column:"));
        panel.add(colCombo);
        panel.add(new JLabel("Operation:"));
        panel.add(opCombo);
        panel.add(new JLabel("Value:"));
        panel.add(valueField);

        int res = JOptionPane.showConfirmDialog(this, panel, "Add Additional Filter on " + activeTableName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String colName = (String) colCombo.getSelectedItem();
        String op = (String) opCombo.getSelectedItem();
        String value = valueField.getText();

        if (value.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a filter value.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Apply additional filter on the already-filtered model
        applyAdditionalFilter(activeModel, colName, op, value);
        
        statusLabel.setText("Applied additional filter on " + activeTableName + "." + colName);
    }

    // Apply an additional filter to an already-filtered model
    private void applyAdditionalFilter(DefaultTableModel model, String colName, String op, String value) {
        final String filterValue = value.toLowerCase();
        int colIndex = model.findColumn(colName);

        // Create new filtered model with same columns
        DefaultTableModel filteredModel = new DefaultTableModel() {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (int i = 0; i < model.getColumnCount(); i++) {
            filteredModel.addColumn(model.getColumnName(i));
        }

        // Filter the current model
        for (int i = 0; i < model.getRowCount(); i++) {
            Object cellVal = model.getValueAt(i, colIndex);
            String cellStr = (cellVal == null ? "" : cellVal.toString()).toLowerCase();
            if (matchesFilter(cellStr, filterValue, op)) {
                Object[] row = new Object[model.getColumnCount()];
                for (int c = 0; c < model.getColumnCount(); c++) {
                    row[c] = model.getValueAt(i, c);
                }
                filteredModel.addRow(row);
            }
        }

        // Update the active table with the further-filtered results
        int activeTab = tabbedPane.getSelectedIndex();
        switch (activeTab) {
            case 0: artistTable.setModel(filteredModel); break;
            case 1: workTable.setModel(filteredModel); break;
            case 2: museumTable.setModel(filteredModel); break;
            case 3: museumHoursTable.setModel(filteredModel); break;
            case 4: subjectTable.setModel(filteredModel); break;
            case 5: workSubjectTable.setModel(filteredModel); break;
            case 6: productSizeTable.setModel(filteredModel); break;
            case 7: canvasSizeTable.setModel(filteredModel); break;
            case 8: imageLinkTable.setModel(filteredModel); break;
        }

        applyRenderers();
    }

    // Logout and return to login screen
    private void onLogoutClicked(ActionEvent ev) {
        int confirm = JOptionPane.showConfirmDialog(this, "Logout and return to login screen?", "Confirm Logout", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            currentUserRole = null;
            activeFilter = null;
            filterColumnName = null;
            activeFilterTableName = null;
            dispose();
            
            // Show login dialog again
            SwingUtilities.invokeLater(() -> {
                MiniViewer v = new MiniViewer("painting.db");
                if (v.currentUserRole != null) {
                    v.setVisible(true);
                }
            });
        }
    }

    // Apply renderers to work and museum tables to color-code related museum ids/rows
    private void applyRenderers() {
        // Work table: color the museum_id cell
        DefaultTableModel wModel = (DefaultTableModel) workTable.getModel();
        int workMuseumCol = -1;
        if (wModel != null) workMuseumCol = wModel.findColumn("museum_id");

        if (workMuseumCol >= 0) {
            javax.swing.table.TableColumn tc = workTable.getColumnModel().getColumn(workMuseumCol);
            tc.setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
                @Override
                public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    java.awt.Color col = null;
                    Integer mid = null;
                    if (value instanceof Number) mid = ((Number) value).intValue();
                    else if (value != null) {
                        try { mid = Integer.parseInt(value.toString()); } catch (Exception ignored) {}
                    }
                    if (mid != null) col = museumColorMap.get(mid);
                    if (col != null && !isSelected) c.setBackground(col);
                    else if (!isSelected) c.setBackground(java.awt.Color.WHITE);
                    return c;
                }
            });
        }

        // Museum table: color entire row if its id is in highlightedMuseumIds
        DefaultTableModel mModel = (DefaultTableModel) museumTable.getModel();
        int museumIdCol = -1;
        if (mModel != null) museumIdCol = mModel.findColumn("id");
        final int museumIdColFinal = museumIdCol;

        javax.swing.table.DefaultTableCellRenderer museumRenderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (museumIdColFinal >= 0) {
                    Object idv = table.getModel().getValueAt(row, museumIdColFinal);
                    Integer mid = null;
                    if (idv instanceof Number) mid = ((Number) idv).intValue();
                    else if (idv != null) {
                        try { mid = Integer.parseInt(idv.toString()); } catch (Exception ignored) {}
                    }
                    java.awt.Color col = (mid == null ? null : museumColorMap.get(mid));
                    if (col != null && !isSelected) c.setBackground(col);
                    else if (!isSelected) c.setBackground(java.awt.Color.WHITE);
                }
                return c;
            }
        };

        if (mModel != null) {
            for (int ci = 0; ci < museumTable.getColumnCount(); ci++) {
                museumTable.getColumnModel().getColumn(ci).setCellRenderer(museumRenderer);
            }
        }
    }

    public static void main(String[] args) {
        // Allow optional DB path as first arg
        String dbPath = args.length > 0 ? args[0] : "painting.db";

        // Ensure SQLite JDBC driver is available — the xerial driver registers itself automatically.
        SwingUtilities.invokeLater(() -> {
            // Show login dialog in the main thread
            MiniViewer v = new MiniViewer(dbPath);
            // Login is shown in constructor, so only set visible if login succeeded
            if (v.currentUserRole != null) {
                v.setVisible(true);
            }
        });
    }
}
