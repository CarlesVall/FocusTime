package com.focustime;

import com.focustime.model.Task;
import com.focustime.model.TimeEntry;
import com.focustime.repository.DatabaseManager;
import com.focustime.repository.RepositoryException;
import com.focustime.repository.TaskRepository;
import com.focustime.repository.TimeEntryRepository;
import com.focustime.service.TaskService;
import com.focustime.service.TimeTrackingService;
import com.focustime.service.TimerService;
import com.focustime.util.TimeFormatUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.stream.Stream;

public class FocusTimeApp extends Application {
    private static final List<DayOfWeek> DAY_ORDER = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    );
    private static final Map<DayOfWeek, String> DAY_SHORT_LABELS = Map.of(
            DayOfWeek.MONDAY, "L",
            DayOfWeek.TUESDAY, "M",
            DayOfWeek.WEDNESDAY, "X",
            DayOfWeek.THURSDAY, "J",
            DayOfWeek.FRIDAY, "V",
            DayOfWeek.SATURDAY, "S",
            DayOfWeek.SUNDAY, "D"
    );
    private static final Locale SPANISH_LOCALE = Locale.of("es", "ES");
    private static final DateTimeFormatter TIME_HMS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TaskService taskService;
    private TimeTrackingService timeTrackingService;
    private TimerService timerService;

    private final ObservableList<Task> allTasks = FXCollections.observableArrayList();
    private final ObservableList<Task> activeTasks = FXCollections.observableArrayList();
    private final ObservableList<TodayEntryRow> todayEntryRows = FXCollections.observableArrayList();

    private FlowPane tasksPane;
    private ListView<Task> activeTaskList;
    private TableView<TodayEntryRow> todayEntriesTable;
    private Label timerLabel;
    private Label selectedTaskLabel;
    private Label sessionsTotalLabel;
    private Button startButton;
    private Button pauseButton;
    private Button cancelButton;
    private Button editSessionButton;
    private Button deleteSessionButton;
    private Button allSessionsButton;
    private Timeline timerTimeline;
    private CalendarMode calendarMode = CalendarMode.MONTHLY;
    private LocalDate calendarReferenceDate = LocalDate.now();
    private GridPane calendarGrid;
    private Label calendarPeriodLabel;
    private VBox calendarOverviewPane;
    private VBox calendarDetailPane;
    private Label calendarDetailDateLabel;
    private Label calendarDetailSummaryLabel;
    private VBox calendarTaskSummaryCardsBox;
    private TableView<CalendarEntryRow> calendarEntryTable;
    private Stage primaryStage;
    private final List<TodayEntryRow> allTodayEntryRows = new ArrayList<>();
    private Map<Long, Long> todaySecondsByTask = Map.of();
    private boolean showAllSessions;

    private enum CalendarMode {
        WEEKLY,
        MONTHLY
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        initializeServices();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        StackPane content = new StackPane();
        content.getStyleClass().add("content-area");
        VBox navigation = createNavigation(content);

        root.setLeft(navigation);
        root.setCenter(content);

        Scene scene = new Scene(root, 1280, 860);
        addStylesheet(scene);
        stage.setTitle("FocusTime");
        stage.setScene(scene);
        stage.setMinWidth(1180);
        stage.setMinHeight(780);
        stage.setOnCloseRequest(event -> {
            if (timerService.hasRunningSessions()) {
                event.consume();
                showInfo("Temporizador activo", "Pausa o cancela la sesion actual antes de cerrar FocusTime.");
            }
        });

        showView(content, createTasksView());
        refreshAll();
        stage.show();
    }

    private void addStylesheet(Scene scene) {
        var stylesheet = getClass().getResource("/com/focustime/styles.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    private void initializeServices() {
        DatabaseManager databaseManager = new DatabaseManager();
        databaseManager.initializeDatabase();
        TaskRepository taskRepository = new TaskRepository(databaseManager);
        TimeEntryRepository timeEntryRepository = new TimeEntryRepository(databaseManager);
        taskService = new TaskService(taskRepository, timeEntryRepository);
        timeTrackingService = new TimeTrackingService(timeEntryRepository);
        timerService = new TimerService(timeTrackingService);
    }

    private VBox createNavigation(StackPane content) {
        Button tasksButton = new Button("Tareas");
        Button todayButton = new Button("Hoy");
        Button calendarButton = new Button("Calendario");

        tasksButton.setMaxWidth(Double.MAX_VALUE);
        todayButton.setMaxWidth(Double.MAX_VALUE);
        calendarButton.setMaxWidth(Double.MAX_VALUE);
        tasksButton.getStyleClass().add("nav-button");
        todayButton.getStyleClass().add("nav-button");
        calendarButton.getStyleClass().add("nav-button");

        tasksButton.setOnAction(event -> {
            refreshAll();
            showView(content, createTasksView());
        });
        todayButton.setOnAction(event -> {
            refreshAll();
            showView(content, createTodayView());
        });
        calendarButton.setOnAction(event -> {
            refreshAll();
            showView(content, createCalendarView());
        });

        Label title = new Label("FocusTime");
        title.getStyleClass().add("app-title");

        VBox navigation = new VBox(12, title, tasksButton, todayButton, calendarButton);
        navigation.setPadding(new Insets(18));
        navigation.setPrefWidth(180);
        navigation.getStyleClass().add("sidebar");
        return navigation;
    }

    private VBox createTasksView() {
        Button addTaskButton = new Button("Agregar tarea");
        addTaskButton.setOnAction(event -> showTaskDialog(null));
        addTaskButton.getStyleClass().add("primary-button");

        Label heading = new Label("Tareas");
        heading.getStyleClass().add("page-title");

        HBox header = new HBox(12, heading, addTaskButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-header");

        tasksPane = new FlowPane();
        tasksPane.setHgap(12);
        tasksPane.setVgap(12);
        tasksPane.setPadding(new Insets(4));
        tasksPane.setPrefWrapLength(760);
        tasksPane.getStyleClass().add("task-grid");

        ScrollPane scrollPane = new ScrollPane(tasksPane);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("content-scroll");

        refreshTaskCards();

        VBox view = new VBox(16, header, scrollPane);
        view.setPadding(new Insets(24));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return view;
    }

    private void showTaskDialog(Task taskToEdit) {
        boolean editing = taskToEdit != null;
        Dialog<ButtonType> dialog = new Dialog<>();
        configureDialogOwner(dialog);
        dialog.setTitle(editing ? "Editar tarea" : "Nueva tarea");
        dialog.setHeaderText(editing ? "Editar tarea" : "Crear tarea");
        var stylesheet = getClass().getResource("/com/focustime/styles.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("task-dialog");

        ButtonType saveButtonType = new ButtonType(editing ? "Guardar" : "Crear", ButtonType.OK.getButtonData());
        ButtonType deleteButtonType = new ButtonType("Eliminar tarea", ButtonBar.ButtonData.LEFT);
        if (editing) {
            dialog.getDialogPane().getButtonTypes().setAll(deleteButtonType, saveButtonType, ButtonType.CANCEL);
            Node deleteButtonNode = dialog.getDialogPane().lookupButton(deleteButtonType);
            if (deleteButtonNode != null) {
                deleteButtonNode.getStyleClass().add("danger-button");
            }
        } else {
            dialog.getDialogPane().getButtonTypes().setAll(saveButtonType, ButtonType.CANCEL);
        }

        TextField nameField = new TextField();
        nameField.setPromptText("Nombre de tarea");
        if (editing) {
            nameField.setText(taskToEdit.getName());
        }

        Spinner<Integer> hourSpinner = new Spinner<>();
        int initialHours = editing ? taskToEdit.getDailyObjectiveMinutes() / 60 : 0;
        hourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, initialHours, 1));
        hourSpinner.setEditable(true);

        Spinner<Integer> minuteSpinner = new Spinner<>();
        int initialMinutes = editing ? taskToEdit.getDailyObjectiveMinutes() % 60 : 30;
        minuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, initialMinutes, 5));
        minuteSpinner.setEditable(true);

        Set<DayOfWeek> selectedDays = editing ? parseScheduledDays(taskToEdit.getScheduledDays()) : Set.copyOf(DAY_ORDER);
        Map<DayOfWeek, ToggleButton> dayButtons = new LinkedHashMap<>();
        HBox daysBox = new HBox(8);
        daysBox.setAlignment(Pos.CENTER_LEFT);
        for (DayOfWeek day : DAY_ORDER) {
            ToggleButton dayButton = new ToggleButton(DAY_SHORT_LABELS.get(day));
            dayButton.getStyleClass().add("day-toggle");
            dayButton.setSelected(selectedDays.contains(day));
            dayButtons.put(day, dayButton);
            daysBox.getChildren().add(dayButton);
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        grid.add(new Label("Nombre"), 0, 0);
        grid.add(nameField, 1, 0, 2, 1);
        grid.add(new Label("Horas"), 0, 1);
        grid.add(hourSpinner, 1, 1);
        grid.add(new Label("Minutos"), 0, 2);
        grid.add(minuteSpinner, 1, 2);
        grid.add(new Label("Dias"), 0, 3);
        grid.add(daysBox, 1, 3, 3, 1);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(result -> {
            if (editing && result == deleteButtonType) {
                confirmAndDeleteTask(taskToEdit);
            } else if (result == saveButtonType) {
                int totalMinutes = hourSpinner.getValue() * 60 + minuteSpinner.getValue();
                String scheduledDays = buildScheduledDays(dayButtons);
                runSafely(() -> {
                    if (editing) {
                        taskService.updateTask(taskToEdit.getId(), nameField.getText(), totalMinutes, scheduledDays);
                    } else {
                        taskService.createTask(nameField.getText(), totalMinutes, scheduledDays);
                    }
                    refreshAll();
                });
            }
        });
    }

    private void confirmAndDeleteTask(Task task) {
        if (task == null) {
            return;
        }
        if (timerService.isRunning(task.getId())) {
            showError("No puedes eliminar una tarea con temporizador en curso.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        configureDialogOwner(alert);
        alert.setTitle("Eliminar tarea");
        alert.setHeaderText("Eliminar tarea y registros");
        alert.setContentText("Se eliminara la tarea y todas sus sesiones registradas. Esta accion no se puede deshacer.");
        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                runSafely(() -> {
                    taskService.deleteTask(task.getId());
                    refreshAll();
                });
            }
        });
    }

    private VBox createTodayView() {
        LocalDate today = LocalDate.now();
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH_LOCALE);
        String weekdayLabel = weekday.substring(0, 1).toUpperCase(SPANISH_LOCALE) + weekday.substring(1);
        Label heading = new Label("Hoy - " + today + " (" + weekdayLabel + ")");
        heading.getStyleClass().add("page-title");

        activeTaskList = new ListView<>(activeTasks);
        activeTaskList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        activeTaskList.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                    setText(null);
                    getStyleClass().remove("running-task-cell");
                    getStyleClass().remove("pending-task-cell");
                    getStyleClass().remove("completed-task-cell");
                } else {
                    boolean runningThisTask = timerService.isRunning(task.getId());
                    long seconds = todaySecondsByTask.getOrDefault(task.getId(), 0L);
                    if (runningThisTask) {
                        seconds += timerService.getElapsed(task.getId()).getSeconds();
                    }
                    boolean completedToday = seconds >= task.getDailyObjectiveMinutes() * 60L;
                    boolean pendingToday = seconds < task.getDailyObjectiveMinutes() * 60L;
                    String runningPrefix = runningThisTask ? "[En curso] " : "";
                    String statusSuffix = completedToday ? " | Completada" : (pendingToday ? " | Pendiente" : "");
                    setText(runningPrefix + task.getName() + " - " + TimeFormatUtils.formatDurationShort(seconds) + " / " + task.getDailyObjectiveMinutes() + " min" + statusSuffix);
                    if (runningThisTask && !getStyleClass().contains("running-task-cell")) {
                        getStyleClass().add("running-task-cell");
                    } else if (!runningThisTask) {
                        getStyleClass().remove("running-task-cell");
                    }
                    if (pendingToday && !getStyleClass().contains("pending-task-cell")) {
                        getStyleClass().add("pending-task-cell");
                    } else if (!pendingToday) {
                        getStyleClass().remove("pending-task-cell");
                    }
                    if (completedToday && !getStyleClass().contains("completed-task-cell")) {
                        getStyleClass().add("completed-task-cell");
                    } else if (!completedToday) {
                        getStyleClass().remove("completed-task-cell");
                    }
                }
            }
        });
        selectedTaskLabel = new Label("Sin tarea seleccionada");
        activeTaskList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            showAllSessions = false;
            selectedTaskLabel.setText(newValue == null ? "Sin tarea seleccionada" : newValue.getName());
            refreshTodayEntryRowsForSelectedTask();
            updateTimerState();
            updateTimerControls();
        });
        timerLabel = new Label("00:00:00");
        timerLabel.getStyleClass().add("timer-display");

        startButton = new Button("Iniciar");
        pauseButton = new Button("Pausar");
        cancelButton = new Button("Cancelar");
        startButton.getStyleClass().add("primary-button");
        pauseButton.getStyleClass().add("secondary-button");
        cancelButton.getStyleClass().add("danger-button");

        startButton.setOnAction(event -> runSafely(() -> {
            Task selectedTask = activeTaskList.getSelectionModel().getSelectedItem();
            timerService.start(selectedTask == null ? null : selectedTask.getId());
            updateTimerState();
            updateTimerControls();
        }));
        pauseButton.setOnAction(event -> runSafely(() -> {
            Task selectedTask = activeTaskList.getSelectionModel().getSelectedItem();
            timerService.stopAndRegister(selectedTask == null ? null : selectedTask.getId(), null);
            refreshAll();
            updateTimerState();
        }));
        cancelButton.setOnAction(event -> runSafely(() -> {
            Task selectedTask = activeTaskList.getSelectionModel().getSelectedItem();
            timerService.cancel(selectedTask == null ? null : selectedTask.getId());
            updateTimerState();
        }));

        HBox controls = new HBox(10, startButton, pauseButton, cancelButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        todayEntriesTable = new TableView<>(todayEntryRows);
        todayEntriesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<TodayEntryRow, String> taskColumn = textColumn("Tarea", TodayEntryRow::taskName);
        taskColumn.setStyle("-fx-font-weight: 800;");
        TableColumn<TodayEntryRow, String> durationColumn = textColumn("Duracion", row -> TimeFormatUtils.formatDuration(row.durationSeconds()));
        durationColumn.setStyle("-fx-font-weight: 800;");
        todayEntriesTable.getColumns().setAll(
                taskColumn,
                durationColumn,
                textColumn("Inicio", row -> formatTime(row.startTime())),
                textColumn("Fin", row -> formatTime(row.endTime()))
        );
        todayEntriesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            boolean hasSelection = newValue != null;
            if (editSessionButton != null) {
                editSessionButton.setDisable(!hasSelection);
            }
            if (deleteSessionButton != null) {
                deleteSessionButton.setDisable(!hasSelection);
            }
        });

        editSessionButton = new Button("Editar sesion");
        deleteSessionButton = new Button("Eliminar sesion");
        editSessionButton.getStyleClass().add("secondary-button");
        deleteSessionButton.getStyleClass().add("danger-button");
        editSessionButton.setDisable(true);
        deleteSessionButton.setDisable(true);
        editSessionButton.setOnAction(event -> {
            TodayEntryRow selectedRow = todayEntriesTable.getSelectionModel().getSelectedItem();
            if (selectedRow != null) {
                showEditSessionDialog(selectedRow);
            }
        });
        deleteSessionButton.setOnAction(event -> {
            TodayEntryRow selectedRow = todayEntriesTable.getSelectionModel().getSelectedItem();
            if (selectedRow != null) {
                confirmAndDeleteSession(selectedRow);
            }
        });
        allSessionsButton = new Button("Todas las tareas");
        allSessionsButton.getStyleClass().add("secondary-button");
        allSessionsButton.setOnAction(event -> {
            showAllSessions = true;
            refreshTodayEntryRowsForSelectedTask();
        });

        HBox sessionActions = new HBox(10, allSessionsButton, editSessionButton, deleteSessionButton);
        sessionActions.setAlignment(Pos.CENTER_LEFT);

        GridPane main = new GridPane();
        main.getStyleClass().add("dashboard-panel");
        main.setHgap(18);
        main.setVgap(12);
        main.add(new Label("Tareas activas"), 0, 0);
        main.add(activeTaskList, 0, 1, 1, 5);
        main.add(new Label("Tarea seleccionada"), 1, 0);
        main.add(selectedTaskLabel, 1, 1);
        main.add(timerLabel, 1, 2);
        main.add(controls, 1, 3);
        GridPane.setHgrow(activeTaskList, Priority.ALWAYS);

        sessionsTotalLabel = new Label("Total mostrado: 00:00:00");
        sessionsTotalLabel.getStyleClass().add("muted-text");

        VBox view = new VBox(16, heading, main, new Label("Sesiones de hoy"), sessionsTotalLabel, sessionActions, todayEntriesTable);
        view.setPadding(new Insets(24));
        VBox.setVgrow(todayEntriesTable, Priority.ALWAYS);

        configureTimerTimeline();
        refreshTodayEntryRowsForSelectedTask();
        updateTimerState();
        updateTimerControls();
        return view;
    }

    private VBox createCalendarView() {
        Label heading = new Label("Calendario");
        heading.getStyleClass().add("page-title");
        ToggleButton weeklyButton = new ToggleButton("Semanal");
        ToggleButton monthlyButton = new ToggleButton("Mensual");
        weeklyButton.getStyleClass().add("secondary-button");
        monthlyButton.getStyleClass().add("secondary-button");
        ToggleGroup modeGroup = new ToggleGroup();
        weeklyButton.setToggleGroup(modeGroup);
        monthlyButton.setToggleGroup(modeGroup);
        if (calendarMode == CalendarMode.WEEKLY) {
            weeklyButton.setSelected(true);
            weeklyButton.getStyleClass().add("primary-button");
        } else {
            monthlyButton.setSelected(true);
            monthlyButton.getStyleClass().add("primary-button");
        }

        weeklyButton.setOnAction(event -> {
            calendarMode = CalendarMode.WEEKLY;
            weeklyButton.getStyleClass().remove("secondary-button");
            if (!weeklyButton.getStyleClass().contains("primary-button")) {
                weeklyButton.getStyleClass().add("primary-button");
            }
            monthlyButton.getStyleClass().remove("primary-button");
            if (!monthlyButton.getStyleClass().contains("secondary-button")) {
                monthlyButton.getStyleClass().add("secondary-button");
            }
            refreshCalendarGrid();
        });
        monthlyButton.setOnAction(event -> {
            calendarMode = CalendarMode.MONTHLY;
            monthlyButton.getStyleClass().remove("secondary-button");
            if (!monthlyButton.getStyleClass().contains("primary-button")) {
                monthlyButton.getStyleClass().add("primary-button");
            }
            weeklyButton.getStyleClass().remove("primary-button");
            if (!weeklyButton.getStyleClass().contains("secondary-button")) {
                weeklyButton.getStyleClass().add("secondary-button");
            }
            refreshCalendarGrid();
        });

        Button prevButton = new Button("<");
        Button nextButton = new Button(">");
        Button todayButton = new Button("Hoy");
        prevButton.getStyleClass().add("secondary-button");
        nextButton.getStyleClass().add("secondary-button");
        todayButton.getStyleClass().add("secondary-button");

        prevButton.setOnAction(event -> {
            calendarReferenceDate = calendarMode == CalendarMode.MONTHLY
                    ? calendarReferenceDate.minusMonths(1)
                    : calendarReferenceDate.minusWeeks(1);
            refreshCalendarGrid();
        });
        nextButton.setOnAction(event -> {
            calendarReferenceDate = calendarMode == CalendarMode.MONTHLY
                    ? calendarReferenceDate.plusMonths(1)
                    : calendarReferenceDate.plusWeeks(1);
            refreshCalendarGrid();
        });
        todayButton.setOnAction(event -> {
            calendarReferenceDate = LocalDate.now();
            refreshCalendarGrid();
        });

        calendarPeriodLabel = new Label();
        calendarPeriodLabel.getStyleClass().add("calendar-period-label");
        HBox controls = new HBox(10, weeklyButton, monthlyButton, prevButton, todayButton, nextButton, calendarPeriodLabel);
        controls.setAlignment(Pos.CENTER_LEFT);

        calendarGrid = new GridPane();
        calendarGrid.getStyleClass().add("calendar-grid");
        calendarGrid.setHgap(8);
        calendarGrid.setVgap(8);
        calendarOverviewPane = new VBox(14, controls, calendarGrid);
        calendarOverviewPane.getStyleClass().add("dashboard-panel");
        VBox.setVgrow(calendarGrid, Priority.ALWAYS);

        Button backButton = new Button("Volver");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(event -> showCalendarOverview());

        calendarDetailDateLabel = new Label();
        calendarDetailDateLabel.getStyleClass().add("calendar-detail-date");
        calendarDetailSummaryLabel = new Label();
        calendarDetailSummaryLabel.getStyleClass().add("muted-text");

        calendarTaskSummaryCardsBox = new VBox(10);
        calendarTaskSummaryCardsBox.getStyleClass().add("calendar-task-cards");

        calendarEntryTable = new TableView<>();
        calendarEntryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<CalendarEntryRow, String> calendarTaskColumn = textColumn("Tarea", CalendarEntryRow::taskName);
        calendarTaskColumn.setStyle("-fx-font-weight: 800;");
        TableColumn<CalendarEntryRow, String> calendarDurationColumn = textColumn("Duracion", row -> TimeFormatUtils.formatDuration(row.durationSeconds()));
        calendarDurationColumn.setStyle("-fx-font-weight: 800;");
        calendarEntryTable.getColumns().setAll(
                calendarTaskColumn,
                calendarDurationColumn,
                textColumn("Inicio", row -> formatTime(row.start())),
                textColumn("Fin", row -> formatTime(row.end()))
        );

        Label breakdownLabel = new Label("Resumen por tarea");
        breakdownLabel.getStyleClass().add("page-subtitle");
        Label sessionsLabel = new Label("Sesiones");
        sessionsLabel.getStyleClass().add("page-subtitle");

        HBox detailHeader = new HBox(12, calendarDetailDateLabel, new HBox(), backButton);
        HBox.setHgrow(detailHeader.getChildren().get(1), Priority.ALWAYS);
        detailHeader.setAlignment(Pos.CENTER_LEFT);

        calendarDetailPane = new VBox(14, detailHeader, calendarDetailSummaryLabel, breakdownLabel, calendarTaskSummaryCardsBox, sessionsLabel, calendarEntryTable);
        calendarDetailPane.getStyleClass().add("dashboard-panel");
        VBox.setVgrow(calendarEntryTable, Priority.ALWAYS);
        calendarDetailPane.setVisible(false);
        calendarDetailPane.setManaged(false);

        refreshCalendarGrid();

        StackPane calendarContent = new StackPane(calendarOverviewPane, calendarDetailPane);
        VBox.setVgrow(calendarContent, Priority.ALWAYS);

        VBox view = new VBox(16, heading, calendarContent);
        view.setPadding(new Insets(24));
        return view;
    }

    private <T> TableColumn<T, String> textColumn(String title, Function<T, String> valueProvider) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(valueProvider.apply(data.getValue())));
        return column;
    }

    private void refreshTaskCards() {
        if (tasksPane == null) {
            return;
        }
        tasksPane.getChildren().setAll(allTasks.stream()
                .map(this::createTaskCard)
                .toList());
    }

    private VBox createTaskCard(Task task) {
        Label nameLabel = new Label(task.getName());
        nameLabel.getStyleClass().add("task-card-title");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        int hours = task.getDailyObjectiveMinutes() / 60;
        int minutes = task.getDailyObjectiveMinutes() % 60;
        String objectiveText = hours > 0
                ? String.format("Objetivo: %d h %02d min", hours, minutes)
                : String.format("Objetivo: %d min", minutes);
        Label objectiveLabel = new Label(objectiveText);
        objectiveLabel.getStyleClass().add("task-card-meta");
        objectiveLabel.setMaxWidth(Double.MAX_VALUE);

        Label daysLabel = new Label("Dias: " + formatScheduledDays(task.getScheduledDays()));
        daysLabel.getStyleClass().add("task-card-meta");
        daysLabel.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(8, nameLabel, objectiveLabel, daysLabel);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14));
        card.setPrefWidth(240);
        card.setMinHeight(92);
        card.getStyleClass().add("task-card");
        if (!task.isActive()) {
            card.getStyleClass().add("task-card-inactive");
        }
        card.setOnMouseClicked(event -> showTaskDialog(task));
        return card;
    }

    private void showEditSessionDialog(TodayEntryRow row) {
        Dialog<ButtonType> dialog = new Dialog<>();
        configureDialogOwner(dialog);
        dialog.setTitle("Editar sesion");
        dialog.setHeaderText("Editar sesion registrada");
        var stylesheet = getClass().getResource("/com/focustime/styles.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("task-dialog");

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().setAll(saveButtonType, ButtonType.CANCEL);

        ComboBox<Task> taskCombo = new ComboBox<>(FXCollections.observableArrayList(allTasks));
        taskCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Task task) {
                return task == null ? "" : task.getName();
            }

            @Override
            public Task fromString(String string) {
                return null;
            }
        });
        taskCombo.setValue(allTasks.stream()
                .filter(task -> task.getId().equals(row.taskId()))
                .findFirst()
                .orElse(null));

        TextField startTimeField = new TextField(row.startTime().format(TIME_HMS_FORMATTER));
        startTimeField.setPromptText("HH:mm:ss");
        TextField endTimeField = new TextField(row.endTime().format(TIME_HMS_FORMATTER));
        endTimeField.setPromptText("HH:mm:ss");
        Label formatHint = new Label("Formato obligatorio: HH:mm:ss");
        formatHint.getStyleClass().add("muted-text");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        grid.add(new Label("Tarea"), 0, 0);
        grid.add(taskCombo, 1, 0, 2, 1);
        grid.add(new Label("Inicio"), 0, 1);
        grid.add(startTimeField, 1, 1, 2, 1);
        grid.add(new Label("Fin"), 0, 2);
        grid.add(endTimeField, 1, 2, 2, 1);
        grid.add(formatHint, 1, 3, 2, 1);
        GridPane.setHgrow(taskCombo, Priority.ALWAYS);
        GridPane.setHgrow(startTimeField, Priority.ALWAYS);
        GridPane.setHgrow(endTimeField, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(result -> {
            if (result == saveButtonType) {
                Task selectedTask = taskCombo.getValue();
                LocalDate date = LocalDate.now();
                runSafely(() -> {
                    LocalTime startTime = parseUserTime(startTimeField.getText());
                    LocalTime endTime = parseUserTime(endTimeField.getText());
                    LocalDateTime start = LocalDateTime.of(date, startTime);
                    LocalDateTime end = LocalDateTime.of(date, endTime);
                    timeTrackingService.updateEntry(row.entryId(), selectedTask == null ? null : selectedTask.getId(), start, end);
                    refreshAll();
                });
            }
        });
    }

    private void confirmAndDeleteSession(TodayEntryRow row) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        configureDialogOwner(alert);
        alert.setTitle("Eliminar sesion");
        alert.setHeaderText("Eliminar registro");
        alert.setContentText("Se eliminara la sesion seleccionada. Esta accion no se puede deshacer.");
        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                runSafely(() -> {
                    timeTrackingService.deleteEntry(row.entryId());
                    refreshAll();
                });
            }
        });
    }

    private void refreshAll() {
        LocalDate today = LocalDate.now();
        List<Task> loadedTasks = taskService.findAllTasks();

        allTasks.setAll(loadedTasks);
        refreshTaskCards();
        activeTasks.setAll(taskService.findActiveTasks().stream()
                .filter(task -> isScheduledForDay(task, today.getDayOfWeek()))
                .toList());
        todaySecondsByTask = timeTrackingService.getSecondsByTaskForDate(today);

        Map<Long, Task> tasksById = loadedTasks.stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        allTodayEntryRows.clear();
        allTodayEntryRows.addAll(timeTrackingService.findEntriesByDate(today).stream()
                .map(entry -> new TodayEntryRow(
                        entry.getId(),
                        entry.getTaskId(),
                        entry.getStartTime().toLocalTime().withNano(0),
                        entry.getEndTime().toLocalTime().withNano(0),
                        entry.getDurationSeconds(),
                        tasksById.get(entry.getTaskId()) == null ? "Tarea eliminada" : tasksById.get(entry.getTaskId()).getName()
                ))
                .sorted(Comparator.comparing(TodayEntryRow::startTime).reversed())
                .toList());
        refreshTodayEntryRowsForSelectedTask();

        if (activeTaskList != null) {
            Task selected = activeTaskList.getSelectionModel().getSelectedItem();
            activeTaskList.refresh();
            if (selected != null && activeTasks.stream().noneMatch(task -> task.getId().equals(selected.getId()))) {
                activeTaskList.getSelectionModel().clearSelection();
            }
        }
        updateTimerState();
    }

    private void configureTimerTimeline() {
        if (timerTimeline != null) {
            timerTimeline.stop();
        }
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateTimerState()));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        timerTimeline.play();
    }

    private void updateTimerState() {
        if (timerLabel == null) {
            return;
        }
        Task selectedTask = activeTaskList == null ? null : activeTaskList.getSelectionModel().getSelectedItem();
        long seconds = 0;
        if (selectedTask != null) {
            seconds = todaySecondsByTask.getOrDefault(selectedTask.getId(), 0L);
            if (timerService.isRunning(selectedTask.getId())) {
                seconds += timerService.getElapsed(selectedTask.getId()).getSeconds();
            }
        }
        timerLabel.setText(TimeFormatUtils.formatDuration(seconds));
        if (activeTaskList != null) {
            activeTaskList.refresh();
        }
        updateTimerControls();
    }

    private void updateTimerControls() {
        Task selectedTask = activeTaskList == null ? null : activeTaskList.getSelectionModel().getSelectedItem();
        boolean runningSelectedTask = selectedTask != null && timerService.isRunning(selectedTask.getId());
        if (startButton != null) {
            startButton.setDisable(selectedTask == null || runningSelectedTask);
        }
        if (pauseButton != null) {
            pauseButton.setDisable(!runningSelectedTask);
        }
        if (cancelButton != null) {
            cancelButton.setDisable(!runningSelectedTask);
        }
    }

    private void refreshTodayEntryRowsForSelectedTask() {
        if (todayEntriesTable == null) {
            return;
        }
        Task selectedTask = activeTaskList == null ? null : activeTaskList.getSelectionModel().getSelectedItem();
        if (showAllSessions || selectedTask == null) {
            todayEntryRows.setAll(allTodayEntryRows);
        } else {
            todayEntryRows.setAll(allTodayEntryRows.stream()
                    .filter(row -> selectedTask.getId().equals(row.taskId()))
                    .toList());
        }
        if (sessionsTotalLabel != null) {
            long totalSeconds = todayEntryRows.stream().mapToLong(TodayEntryRow::durationSeconds).sum();
            sessionsTotalLabel.setText("Total mostrado: " + TimeFormatUtils.formatDuration(totalSeconds));
        }
        if (allSessionsButton != null) {
            allSessionsButton.getStyleClass().remove("primary-button");
            if (showAllSessions) {
                allSessionsButton.getStyleClass().remove("secondary-button");
                if (!allSessionsButton.getStyleClass().contains("primary-button")) {
                    allSessionsButton.getStyleClass().add("primary-button");
                }
            } else {
                allSessionsButton.getStyleClass().remove("primary-button");
                if (!allSessionsButton.getStyleClass().contains("secondary-button")) {
                    allSessionsButton.getStyleClass().add("secondary-button");
                }
            }
        }
    }

    private void refreshCalendarGrid() {
        if (calendarGrid == null) {
            return;
        }

        calendarGrid.getChildren().clear();
        LocalDate rangeStart;
        LocalDate rangeEnd;
        LocalDate currentDate = LocalDate.now();

        if (calendarMode == CalendarMode.MONTHLY) {
            YearMonth yearMonth = YearMonth.from(calendarReferenceDate);
            LocalDate firstOfMonth = yearMonth.atDay(1);
            rangeStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            rangeEnd = rangeStart.plusDays(41);
            calendarPeriodLabel.setText(formatMonthPeriod(yearMonth));
        } else {
            rangeStart = calendarReferenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            rangeEnd = rangeStart.plusDays(6);
            calendarPeriodLabel.setText(formatWeekPeriod(rangeStart, rangeEnd));
        }

        Map<LocalDate, Long> totalsByDate = timeTrackingService.getTotalSecondsByDateRange(rangeStart, rangeEnd);
        Map<LocalDate, Integer> calendarEntryCountByDate = timeTrackingService.getEntryCountByDateRange(rangeStart, rangeEnd);
        addCalendarHeaders();

        int totalDays = calendarMode == CalendarMode.MONTHLY ? 42 : 7;
        for (int index = 0; index < totalDays; index++) {
            LocalDate date = rangeStart.plusDays(index);
            long totalSeconds = totalsByDate.getOrDefault(date, 0L);
            int totalEntries = calendarEntryCountByDate.getOrDefault(date, 0);
            boolean inCurrentMonth = calendarMode == CalendarMode.WEEKLY
                    || date.getMonth() == calendarReferenceDate.getMonth();
            VBox dayCell = createCalendarDayCell(date, totalSeconds, totalEntries, inCurrentMonth, currentDate);
            int row = calendarMode == CalendarMode.MONTHLY ? 1 + (index / 7) : 1;
            int col = index % 7;
            calendarGrid.add(dayCell, col, row);
            GridPane.setHgrow(dayCell, Priority.ALWAYS);
            GridPane.setVgrow(dayCell, Priority.ALWAYS);
        }
    }

    private void addCalendarHeaders() {
        for (int i = 0; i < DAY_ORDER.size(); i++) {
            DayOfWeek day = DAY_ORDER.get(i);
            Label header = new Label(day.getDisplayName(TextStyle.SHORT, SPANISH_LOCALE));
            header.getStyleClass().add("calendar-weekday-header");
            calendarGrid.add(header, i, 0);
            GridPane.setHgrow(header, Priority.ALWAYS);
        }
    }

    private VBox createCalendarDayCell(LocalDate date, long totalSeconds, int totalEntries, boolean inCurrentMonth, LocalDate today) {
        Label dayNumber = new Label(Integer.toString(date.getDayOfMonth()));
        dayNumber.getStyleClass().add("calendar-day-number");

        Label totalLabel = new Label(totalSeconds > 0 ? TimeFormatUtils.formatDurationShort(totalSeconds) : "");
        totalLabel.getStyleClass().add("calendar-day-total");
        Label entriesLabel = new Label(totalEntries > 0 ? totalEntries + " sesion(es)" : "");
        entriesLabel.getStyleClass().add("calendar-day-meta");

        VBox dayCell = new VBox(4, dayNumber, totalLabel, entriesLabel);
        dayCell.getStyleClass().add("calendar-day-cell");
        if (!inCurrentMonth) {
            dayCell.getStyleClass().add("calendar-day-outside-month");
        }
        if (totalSeconds > 0) {
            dayCell.getStyleClass().add("calendar-day-with-records");
        }
        if (date.equals(today)) {
            dayCell.getStyleClass().add("calendar-day-today");
        }
        dayCell.setOnMouseClicked(event -> showCalendarDayDetail(date));
        return dayCell;
    }

    private void showCalendarDayDetail(LocalDate date) {
        List<TimeEntry> entries = timeTrackingService.findEntriesByDate(date);
        Map<Long, Task> tasksById = allTasks.stream().collect(Collectors.toMap(Task::getId, Function.identity()));

        long totalSeconds = entries.stream().mapToLong(TimeEntry::getDurationSeconds).sum();
        calendarDetailDateLabel.setText(formatCalendarDate(date));
        calendarDetailSummaryLabel.setText("Total registrado: " + TimeFormatUtils.formatDuration(totalSeconds) + " | Sesiones: " + entries.size());

        Map<Long, Long> totalByTask = entries.stream()
                .collect(Collectors.groupingBy(TimeEntry::getTaskId, Collectors.summingLong(TimeEntry::getDurationSeconds)));
        List<VBox> summaryCards = totalByTask.entrySet().stream()
                .sorted(Comparator.comparing(entry -> {
                    Task task = tasksById.get(entry.getKey());
                    return task == null ? "Tarea eliminada" : task.getName();
                }))
                .map(entry -> {
                    Task task = tasksById.get(entry.getKey());
                    String taskName = task == null ? "Tarea eliminada" : task.getName();
                    int objectiveMinutes = task == null ? 0 : task.getDailyObjectiveMinutes();
                    long taskTotalSeconds = entry.getValue();
                    return createCalendarTaskSummaryCard(taskName, taskTotalSeconds, objectiveMinutes);
                })
                .toList();
        calendarTaskSummaryCardsBox.getChildren().setAll(summaryCards);

        List<CalendarEntryRow> entryRows = entries.stream()
                .map(entry -> {
                    Task task = tasksById.get(entry.getTaskId());
                    return new CalendarEntryRow(
                            task == null ? "Tarea eliminada" : task.getName(),
                            entry.getStartTime().toLocalTime().withNano(0),
                            entry.getEndTime().toLocalTime().withNano(0),
                            entry.getDurationSeconds()
                    );
                })
                .sorted(Comparator.comparing(CalendarEntryRow::start).reversed())
                .toList();
        calendarEntryTable.getItems().setAll(entryRows);

        calendarOverviewPane.setVisible(false);
        calendarOverviewPane.setManaged(false);
        calendarDetailPane.setVisible(true);
        calendarDetailPane.setManaged(true);
    }

    private VBox createCalendarTaskSummaryCard(String taskName, long totalSeconds, int objectiveMinutes) {
        Label title = new Label(taskName);
        title.getStyleClass().add("calendar-task-card-title");

        String objectiveText = objectiveMinutes > 0
                ? TimeFormatUtils.formatDurationShort(objectiveMinutes * 60L)
                : "-";
        Label detail = new Label(TimeFormatUtils.formatDuration(totalSeconds) + " / " + objectiveText);
        detail.getStyleClass().add("calendar-task-card-detail");

        VBox card = new VBox(4, title, detail);
        card.getStyleClass().add("calendar-task-card");
        if (objectiveMinutes > 0 && totalSeconds >= objectiveMinutes * 60L) {
            card.getStyleClass().add("calendar-task-card-completed");
        } else {
            card.getStyleClass().add("calendar-task-card-pending");
        }
        return card;
    }

    private void showCalendarOverview() {
        calendarDetailPane.setVisible(false);
        calendarDetailPane.setManaged(false);
        calendarOverviewPane.setVisible(true);
        calendarOverviewPane.setManaged(true);
    }

    private String formatMonthPeriod(YearMonth yearMonth) {
        String month = yearMonth.getMonth().getDisplayName(TextStyle.FULL, SPANISH_LOCALE);
        return month.substring(0, 1).toUpperCase(SPANISH_LOCALE) + month.substring(1) + " " + yearMonth.getYear();
    }

    private String formatWeekPeriod(LocalDate start, LocalDate end) {
        String startMonth = start.getMonth().getDisplayName(TextStyle.FULL, SPANISH_LOCALE);
        String endMonth = end.getMonth().getDisplayName(TextStyle.FULL, SPANISH_LOCALE);
        if (start.getMonth() == end.getMonth()) {
            return String.format("Semana del %d al %d de %s de %d", start.getDayOfMonth(), end.getDayOfMonth(), startMonth, start.getYear());
        }
        return String.format("Semana del %d de %s al %d de %s de %d", start.getDayOfMonth(), startMonth, end.getDayOfMonth(), endMonth, end.getYear());
    }

    private String formatCalendarDate(LocalDate date) {
        String dayName = date.getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH_LOCALE);
        String monthName = date.getMonth().getDisplayName(TextStyle.FULL, SPANISH_LOCALE);
        return String.format("%s, %d de %s de %d",
                dayName.substring(0, 1).toUpperCase(SPANISH_LOCALE) + dayName.substring(1),
                date.getDayOfMonth(),
                monthName,
                date.getYear());
    }

    private LocalTime parseUserTime(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Introduce una hora valida en formato HH:mm:ss.");
        }
        try {
            return LocalTime.parse(normalized, TIME_HMS_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Formato de hora invalido. Usa HH:mm:ss.");
        }
    }

    private String formatTime(LocalTime time) {
        return time.format(TIME_HMS_FORMATTER);
    }

    private String buildScheduledDays(Map<DayOfWeek, ToggleButton> dayButtons) {
        return DAY_ORDER.stream()
                .filter(day -> dayButtons.get(day).isSelected())
                .map(day -> Integer.toString(day.getValue()))
                .collect(Collectors.joining(","));
    }

    private Set<DayOfWeek> parseScheduledDays(String scheduledDays) {
        if (scheduledDays == null || scheduledDays.isBlank()) {
            return Set.copyOf(DAY_ORDER);
        }
        return scheduledDays.lines()
                .flatMap(line -> Stream.of(line.split(",")))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> {
                    try {
                        return Integer.parseInt(token);
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .filter(value -> value >= 1 && value <= 7)
                .map(DayOfWeek::of)
                .collect(Collectors.toSet());
    }

    private boolean isScheduledForDay(Task task, DayOfWeek dayOfWeek) {
        return parseScheduledDays(task.getScheduledDays()).contains(dayOfWeek);
    }

    private String formatScheduledDays(String scheduledDays) {
        Set<DayOfWeek> days = parseScheduledDays(scheduledDays);
        return DAY_ORDER.stream()
                .filter(days::contains)
                .map(DAY_SHORT_LABELS::get)
                .collect(Collectors.joining(" "));
    }

    private void showView(StackPane content, VBox view) {
        content.getChildren().setAll(view);
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | RepositoryException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        configureDialogOwner(alert);
        alert.setTitle("FocusTime");
        alert.setHeaderText("No se pudo completar la accion");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        configureDialogOwner(alert);
        alert.setTitle("FocusTime");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void configureDialogOwner(Dialog<?> dialog) {
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
    }

    private record CalendarEntryRow(String taskName, LocalTime start, LocalTime end, long durationSeconds) {
    }

    private record TodayEntryRow(Long entryId, Long taskId, LocalTime startTime, LocalTime endTime, long durationSeconds, String taskName) {
    }
}
