package main.java;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Main extends Application {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    private List<String> originalDatabase;
    private List<String> filteredDatabase;
    private final ReadWriteLock lock = new ReentrantReadWriteLock(); // Dodanie ReadWriteLock

    private TextField inputField;
    private ListView<String> wordList;
    private Label infoLabel;

    // Tablica zawierająca polskie litery
    private static final String POLISH_LETTERS = "aąbcćdeęfghijklłmnńoópqrsśtuvwxyzźż";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        originalDatabase = new ArrayList<>();
        filteredDatabase = new ArrayList<>(); // Usunięcie synchronizedList, bo używamy ReadWriteLock

        // Wczytaj plik words.txt z katalogu zasobów
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/main/resources/words.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                originalDatabase.addAll(Arrays.asList(line.split("\\s+")));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Błąd podczas wczytywania pliku words.txt", e);
        }

        filteredDatabase.addAll(originalDatabase);

        primaryStage.setTitle("Literakowanie");

        // Dodanie logo symbolu karo
        Image logoImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/main/resources/literakowanie.png")));
        ImageView logoImageView = new ImageView(logoImage);
        logoImageView.setFitWidth(30);
        logoImageView.setFitHeight(30);

        primaryStage.getIcons().add(logoImage);

        // Utworzenie reszty interfejsu
        inputField = new TextField();
        inputField.setPromptText("Wprowadź litery");
        inputField.setOnKeyReleased(event -> handleKeyReleased(inputField.getText()));

        wordList = new ListView<>();

        infoLabel = new Label("");
        infoLabel.setStyle("-fx-text-fill: red;");

        Button clearButton = new Button("Wyczyść");
        clearButton.setOnAction(event -> clearInput());

        // Komponenty układane w VBox
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        vbox.getChildren().addAll(inputField, infoLabel, wordList, clearButton);

        Scene scene = new Scene(vbox, 300, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void handleKeyReleased(String inputLetters) {
        // Przefiltruj, aby pozostawić tylko małe litery, polskie litery i spacje
        inputLetters = inputLetters.toLowerCase().replaceAll("[^aąbcćdeęfghijklłmnńoópqrsśtuvwxyzźż ]", "");
        if (inputLetters.contains(" ") && inputLetters.indexOf(" ") != inputLetters.lastIndexOf(" ")) {
            String newText = inputLetters.substring(0, inputLetters.length() - 1);
            inputField.setText(newText);
            inputField.positionCaret(newText.length());
            infoLabel.setText("Nie można wprowadzać więcej niż jednej spacji!");
            infoLabel.setStyle("-fx-text-fill: red;");
        } else {
            if (!inputLetters.isEmpty()) {
                infoLabel.setText("Szukam...");
                infoLabel.setStyle("-fx-text-fill: red;");

                final String finalInputLetters = inputLetters;

                new Thread(() -> {
                    List<String> foundWords = findWords(finalInputLetters);

                    Platform.runLater(() -> updateWordList(foundWords));
                }).start();
            } else {
                clearInput();
            }
        }
    }

    private void clearInput() {
        Platform.runLater(() -> {
            inputField.clear();
            wordList.getItems().clear();
            infoLabel.setText("");

            lock.writeLock().lock(); // Blokada zapisu przy czyszczeniu listy
            try {
                filteredDatabase.clear();
                filteredDatabase.addAll(originalDatabase);
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    private List<String> findWords(String inputLetters) {
        List<String> foundWords = new ArrayList<>();

        lock.readLock().lock(); // Blokada odczytu przy dostępie do filteredDatabase
        try {
            List<String> localDatabase = new ArrayList<>(filteredDatabase);

            if (inputLetters.contains(" ")) {
                for (char c : POLISH_LETTERS.toCharArray()) {
                    String inputWithReplacement = inputLetters.replaceFirst(" ", String.valueOf(c));
                    foundWords.addAll(localDatabase.stream()
                            .filter(Objects::nonNull)
                            .filter(word -> canFormWord(word.toLowerCase(), inputWithReplacement))
                            .collect(Collectors.toList()));
                }
            } else {
                foundWords.addAll(localDatabase.stream()
                        .filter(Objects::nonNull)
                        .filter(word -> canFormWord(word.toLowerCase(), inputLetters))
                        .collect(Collectors.toList()));
            }
        } finally {
            lock.readLock().unlock();
        }

        return foundWords;
    }

    private boolean canFormWord(String word, String inputLetters) {
        if (word == null || inputLetters == null || word.length() != inputLetters.length()) {
            return false;
        }

        Map<Character, Integer> letterCounts = new HashMap<>();
        for (char c : POLISH_LETTERS.toCharArray()) {
            letterCounts.put(c, 0);
        }

        // Zlicz wystąpienia liter w słowie
        for (char c : word.toCharArray()) {
            letterCounts.put(c, letterCounts.get(c) + 1);
        }

        // Sprawdź czy litery w słowie można ułożyć z podanych liter
        for (char c : inputLetters.toCharArray()) {
            if (c == ' ') continue;
            if (letterCounts.get(c) == null || letterCounts.get(c) - 1 < 0) {
                return false;
            }
            letterCounts.put(c, letterCounts.get(c) - 1);
        }

        return true;
    }

    private void updateWordList(List<String> foundWords) {
        Platform.runLater(() -> {
            foundWords.sort(String::compareToIgnoreCase);

            ObservableList<String> words = FXCollections.observableArrayList(foundWords);
            wordList.setItems(words);

            if (foundWords.isEmpty()) {
                infoLabel.setText("Nie znaleziono żadnego słowa.");
                infoLabel.setStyle("-fx-text-fill: red;");
            } else {
                infoLabel.setText("Oto pasujące słowa:");
                infoLabel.setStyle("-fx-text-fill: green;");
            }
        });
    }
}
