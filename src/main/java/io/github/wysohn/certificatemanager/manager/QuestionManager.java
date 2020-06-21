package io.github.wysohn.certificatemanager.manager;

import io.github.wysohn.certificatemanager.objects.Question;
import io.github.wysohn.certificatemanager.objects.Questions;
import io.github.wysohn.rapidframework2.core.main.PluginMain;
import util.JarUtil;

import java.io.File;
import java.util.*;

public class QuestionManager extends PluginMain.Manager {
    private final Map<String, Questions> questionsMap = new HashMap<>();
    private File folder;

    public QuestionManager(int loadPriority) {
        super(loadPriority);
    }

    @Override
    public void preload() throws Exception {
        folder = new File(main().getPluginDirectory(), "QuizBank");
    }

    @Override
    public void enable() throws Exception {

    }

    @Override
    public void load() throws Exception {
        if (!folder.exists())
            JarUtil.copyFromJar(getClass(),
                    "QuizBank/*",
                    main().getPluginDirectory(),
                    JarUtil.CopyOption.COPY_IF_NOT_EXIST);

        File[] certQuestionFolders = folder.listFiles(File::isDirectory);
        if (certQuestionFolders == null)
            return;

        for (File certQuestionFolder : certQuestionFolders) {
            String certName = certQuestionFolder.getName();
            questionsMap.put(certName, new Questions(certQuestionFolder));
        }
    }

    @Override
    public void disable() throws Exception {

    }

    public List<Question> getQuestions(String certName, Locale locale) {
        return getQuestions(certName, locale.getLanguage());
    }

    public List<Question> getQuestions(String certName, String localeCode) {
        return Optional.of(certName)
                .map(questionsMap::get)
                .map(questions -> questions.getQuestions(localeCode))
                .orElseGet(ArrayList::new);
    }
}
