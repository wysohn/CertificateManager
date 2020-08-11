package io.github.wysohn.certificatemanager.mediator;

import io.github.wysohn.certificatemanager.main.CertificateManagerLangs;
import io.github.wysohn.certificatemanager.manager.CertificateExamManager;
import io.github.wysohn.certificatemanager.manager.QuestionManager;
import io.github.wysohn.certificatemanager.objects.CertificateExam;
import io.github.wysohn.certificatemanager.objects.Question;
import io.github.wysohn.certificatemanager.objects.User;
import io.github.wysohn.certificatemanager.objects.events.PlayerExamFinishedEvent;
import io.github.wysohn.rapidframework2.bukkit.utils.conversation.ConversationBuilder;
import io.github.wysohn.rapidframework2.core.main.PluginMain;
import io.github.wysohn.rapidframework2.core.manager.common.message.MessageBuilder;
import io.github.wysohn.rapidframework2.core.manager.lang.DefaultLangs;
import org.bukkit.Bukkit;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import util.Sampling;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExamMediator extends PluginMain.Mediator {
    public static final long SECONDS = 1000L;
    public static final long DAYS = SECONDS * 60L * 60L * 24L;

    public static final String ANSWER_INDEX = "answerIndex";
    public static final String NUM_CORRECT = "numCorrect";
    public static final String FEEDBACKS = "Feedbacks";
    public static final String QUESTION = "question";
    public static final String ANSWERS = "answers";

    public boolean deleteCertificate(User examTaker, String certificateName) {
        CertificateExam certificateExam = certificateExamManager.getExam(certificateName);
        if (certificateExam == null) {
            return false;
        }

        return examTaker.removeCertificate(certificateName);
    }

    private CertificateExamManager certificateExamManager;
    private QuestionManager questionManager;

    @Override
    public void enable() throws Exception {
        certificateExamManager = main().getManager(CertificateExamManager.class).get();
        questionManager = main().getManager(QuestionManager.class).get();
    }

    @Override
    public void load() throws Exception {

    }

    @Override
    public void disable() throws Exception {

    }

    public Set<String> getCertificateNames(){
        return certificateExamManager.getExamNames();
    }

    public List<ExamPair> getCertificateExams(){
        return certificateExamManager.getExams();
    }

    public static class ExamPair{
        public final String key;
        public final CertificateExam exam;

        public ExamPair(String key, CertificateExam exam) {
            this.key = key;
            this.exam = exam;
        }
    }

    /**
     *
     * @param examTaker
     * @param certificateName
     * @return null if the certificate with given name doesn't exist; Set of prerequisites otherwise. Set can be empty
     * if all requirements are met.
     */
    public Set<String> missingPrerequisites(User examTaker, String certificateName) {
        CertificateExam certificateExam = certificateExamManager.getExam(certificateName);
        if (certificateExam == null) {
            return null;
        }

        return certificateExam.getPreRequisites().stream()
                .filter(required -> !examTaker.containsCertificate(required))
                .collect(Collectors.toSet());
    }

    public boolean addCertificate(User examTaker, String certificateName) {
        CertificateExam certificateExam = certificateExamManager.getExam(certificateName);
        if (certificateExam == null)
            return false;

        examTaker.addCertificate(certificateName);
        if (certificateExam.getExpireAfterDays() > 0) {
            examTaker.setExpiration(certificateName,
                    System.currentTimeMillis() + certificateExam.getExpireAfterDays() * DAYS);
        }

        return true;
    }

    /**
     * Start taking exam. This does not check if prerequisites are met.
     * Make sure to check first with {@link #missingPrerequisites(User, String)}
     *
     * @param examTaker
     * @param certificateName
     * @param callback
     */
    public void takeExam(User examTaker, String certificateName, ExamResultHandle callback) {
        CertificateExam certificateExam = certificateExamManager.getExam(certificateName);
        if (certificateExam == null) {
            callback.accept(ExamResult.NOT_EXIST, certificateName);
            return;
        }

        if (examTaker.containsCertificate(certificateName)) {
            long expire = examTaker.getExpireDate(certificateName);
            if (expire < 0L) { // never expire
                callback.accept(ExamResult.DUPLICATE, expire);
                return;
            } else if (System.currentTimeMillis() < expire) { // not yet passed expiration
                callback.accept(ExamResult.DUPLICATE, expire);
                return;
            }
        }

        long retakeDue = examTaker.getRetakeDue(certificateName);
        if(retakeDue > -1L && System.currentTimeMillis() < retakeDue){
            callback.accept(ExamResult.RETAKE_DELAY, retakeDue);
            return;
        }

        List<Question> questions = questionManager.getQuestions(certificateName, examTaker.getLocale());
        if(questions.isEmpty()){
            callback.accept(ExamResult.NO_QUESTIONS);
            return;
        }

        ConversationBuilder builder = ConversationBuilder.of(main());

        // show welcome
        builder.doTask(context -> main().lang().sendMessage(examTaker, CertificateManagerLangs.CertificateExamManager_Welcome,
                ((sen, langman) -> langman.addString(certificateExam.getTitle(examTaker.getLocale()))
                        .addString(certificateExam.getDesc(examTaker.getLocale()))),
                true));
        builder.appendConfirm((context) -> {
        });

        // for each question
        int numQuestions = Math.max(1, Math.min(questions.size(), certificateExam.getNumQuestions()));
        int[] indices = Sampling.uniform(numQuestions, numQuestions, false);
        for (int qIndex = 1; qIndex <= questions.size() && qIndex - 1 < indices.length; qIndex++) {
            Question question = questions.get(indices[qIndex - 1]);

            // show prompt
            int finalQIndex = qIndex;
            builder.doTask((context) -> {
                String prompt = question.getQuestion();
                String[] answers = question.getAnswers();
                if (answers.length < 2)
                    throw new RuntimeException("Question " + prompt + " of " + certificateName + " must have at least 2 answers.");

                int[] answerIndices = Sampling.uniform(answers.length, answers.length, false);

                main().lang().sendMessage(examTaker, DefaultLangs.General_Line, true);
                main().lang().sendRawMessage(examTaker, MessageBuilder.forMessage("  &6" + prompt)
                                .build(),
                        true);

                int visibleIndex = 1;
                for (int answerIndex : answerIndices) {
                    if (answerIndex == 0) { // first element is always answer
                        context.setSessionData(ANSWER_INDEX, visibleIndex);
                    }

                    main().lang().sendRawMessage(examTaker, MessageBuilder.forMessage("    &d[" + visibleIndex + "] &f")
                            .append(answers[answerIndex])
                            .withClickRunCommand(String.valueOf(visibleIndex))
                            .withHoverShowText("&7A." + visibleIndex)
                            .build(), true);

                    visibleIndex++;
                }

                main().lang().sendMessage(examTaker, DefaultLangs.General_Line, true);

                context.setSessionData(QUESTION, prompt);
                context.setSessionData(ANSWERS, IntStream.of(answerIndices)
                        .mapToObj(i -> answers[i])
                        .toArray(String[]::new));
            });

            // show list of answers and accept index
            builder.appendInt((context, selectionI) -> {
                int totalAnswers = question.getAnswers().length;
                if (selectionI < 1 || selectionI > totalAnswers)
                    return false;

                int correctI = (int) context.getSessionData(ANSWER_INDEX);
                if (correctI == selectionI) {
                    int numCorrect = Optional.ofNullable(context.getSessionData(NUM_CORRECT))
                            .map(Integer.class::cast)
                            .orElse(0);
                    context.setSessionData(NUM_CORRECT, numCorrect + 1);
                }

                if (certificateExam.isShowFeedback()) {
                    String prompt = (String) context.getSessionData(QUESTION);
                    String[] answers = (String[]) context.getSessionData(ANSWERS);

                    List<Runnable> feedbackList = Optional.ofNullable(context.getSessionData(FEEDBACKS))
                            .map(List.class::cast)
                            .orElseGet(ArrayList::new);

                    feedbackList.add(() -> {
                        main().lang().sendMessage(examTaker, CertificateManagerLangs.CertificateExamManager_Feedback_Question, (lan, man) ->
                                        man.addInteger(finalQIndex).addString(prompt),
                                true);
                        for (int k = 1; k <= answers.length; k++) {
                            String mark = "";
                            if (k == selectionI)
                                mark = "&e<";
                            if (k == correctI)
                                mark = "&a\u2714";

                            int finalK = k;
                            String finalMark = mark;
                            main().lang().sendMessage(examTaker, CertificateManagerLangs.CertificateExamManager_Feedback_Answer, (lan, man) ->
                                            man.addInteger(finalK).addString(answers[finalK - 1]).addString(finalMark),
                                    true);
                        }

                        main().lang().sendRawMessage(examTaker, MessageBuilder.empty(), true);
                    });

                    context.setSessionData(FEEDBACKS, feedbackList);
                }
                return true;
            });
        }

        // show result
        builder.doTask((context) -> {
            // reset retake timer
            if(certificateExam.isRetake() && certificateExam.getRetakeAfterSeconds() > 0) {
                examTaker.setRetakeDue(certificateName,
                        System.currentTimeMillis() + certificateExam.getRetakeAfterSeconds() * SECONDS);
            }

            // show feedbacks
            Optional.ofNullable(context.getSessionData(FEEDBACKS))
                    .ifPresent(o -> ((List<Runnable>) o).forEach(Runnable::run));

            // show final result
            int numCorrect = Optional.ofNullable(context.getSessionData(NUM_CORRECT))
                    .map(Integer.class::cast)
                    .orElse(0);
            double correctPct = (double) numCorrect / questions.size();

            String resultParsed;
            if(correctPct >= certificateExam.getPassingGrade()) {
                resultParsed = main().lang().parseFirst(examTaker, CertificateManagerLangs.CertificateExamManager_Pass);

                examTaker.addCertificate(certificateName);
                if (certificateExam.getExpireAfterDays() > 0) {
                    examTaker.setExpiration(certificateName,
                            System.currentTimeMillis() + certificateExam.getExpireAfterDays() * DAYS);
                }

                certificateExam.getRewards().forEach(reward -> reward.reward(main(), examTaker));

                callback.accept(ExamResult.PASS);
            } else {
                resultParsed = main().lang().parseFirst(examTaker, CertificateManagerLangs.CertificateExamManager_Fail);

                callback.accept(ExamResult.FAIL);
            }

            main().lang().sendMessage(examTaker, CertificateManagerLangs.CertificateExamManager_Result, (sen, man) ->
                    man.addInteger(numCorrect).addInteger(questions.size()).addDouble(correctPct * 100)
                            .addDouble(certificateExam.getPassingGrade() * 100)
                            .addString(resultParsed), true);

            context.setSessionData(EXAM_RESULT, correctPct >= certificateExam.getPassingGrade());
        });

        Conversation conversation = builder.build(examTaker.getSender());
        conversation.addConversationAbandonedListener(event -> {
            callResultEvent(examTaker, certificateName, certificateExam, Optional.of(event)
                    .map(ConversationAbandonedEvent::getContext)
                    .map(context -> context.getSessionData(EXAM_RESULT))
                    .map(Boolean.class::cast)
                    .orElse(false));
            callback.accept(ExamResult.ABANDONED);
        });
        conversation.begin();
    }

    private void callResultEvent(User examTaker, String certificateName, CertificateExam certificateExam, boolean b) {
        Bukkit.getPluginManager().callEvent(new PlayerExamFinishedEvent(examTaker,
                certificateName,
                certificateExam,
                b));
    }

    public static final String EXAM_RESULT = "examResult";

    public enum ExamResult {
        NOT_EXIST, DUPLICATE, RETAKE_DELAY, NO_QUESTIONS, ABANDONED, PASS, FAIL
    }

    @FunctionalInterface
    public interface ExamResultHandle {
        default void accept(ExamResult result) {
            accept(result, new Object[]{});
        }

        void accept(ExamResult result, Object... params);
    }
}
