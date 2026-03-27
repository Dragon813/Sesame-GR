package io.github.dragon813.sesame.model.normal.answerAI;

import io.github.dragon813.sesame.data.Model;
import io.github.dragon813.sesame.data.ModelFields;
import io.github.dragon813.sesame.data.ModelGroup;
import io.github.dragon813.sesame.data.TokenConfig;
import io.github.dragon813.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.dragon813.sesame.data.modelFieldExt.StringModelField;
import io.github.dragon813.sesame.data.modelFieldExt.TextModelField;
import io.github.dragon813.sesame.util.Log;

import java.util.List;
import java.util.Objects;

public class AnswerAI extends Model {

    private static final String TAG = AnswerAI.class.getSimpleName();

    private static Boolean enable = false;

    @Override
    public String getName() {
        return "AI答";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    private static AnswerAIInterface answerAIInterface;

    private final ChoiceModelField aiType = new ChoiceModelField("useGeminiAI", "AI类型", AIType.TONGYI, AIType.nickNames);

    private final TextModelField.UrlTextModelField getTongyiAIToken = new TextModelField.UrlTextModelField("getTongyiAIToken", "通义千问 | 获取令牌", "https://help.aliyun.com/zh/dashscope/developer-reference/acquisition-and-configuration-of-api-key");

    private final StringModelField setTongyiAIToken = new StringModelField("setTongyiAIToken", "通义千问 | 设置令牌", "");

    private final StringModelField setGeminiAIToken = new StringModelField("useGeminiAIToken", "GeminiAI | 设置令牌", "");

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(aiType);
        modelFields.addField(getTongyiAIToken);
        modelFields.addField(setTongyiAIToken);
        modelFields.addField(setGeminiAIToken);
        return modelFields;
    }

    @Override
    public void boot(ClassLoader classLoader) {
        enable = getEnableField().getValue();
        switch (aiType.getValue()) {
            case AIType.TONGYI:
                answerAIInterface = new TongyiAI(setTongyiAIToken.getValue());
                break;
            case AIType.GEMINI:
                answerAIInterface = new GeminiAI(setGeminiAIToken.getValue());
                break;
            default:
                answerAIInterface = AnswerAIInterface.getInstance();
                break;
        }
    }

    /**
     * 获取AI回答结果
     *
     * @param text 问题内容
     * @return AI回答结果
     */
    public static String getAnswer(String text) {
        try {
            if (enable) {
                Log.record("AI🧠答题，问题：[" + text + "]");
                return answerAIInterface.getAnswerStr(text);
            } else {
                Log.record("开始答题，问题：[" + text + "]");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return "";
    }

    /**
     * 获取答案
     *
     * @param text     问题
     * @param answerList 答案集合
     * @return 空没有获取到
     */
    public static String getAnswer(String text, List<String> answerList) {
        String answerStr = "";
        try {
            Log.record("知识问答🧠题目[" + text + "]#选项" + answerList);
            if (enable) {
                Integer answer = answerAIInterface.getAnswer(text, answerList);
                if (answer != null && answer >= 0 && answer < answerList.size()) {
                    answerStr = answerList.get(answer);
                    Log.record("智能回答🧠[" + answerStr + "]");
                }
            } else {
                if (!answerList.isEmpty()) {
                    answerStr = answerList.get(0);
                    Log.record("普通回答🤖[" + answerStr + "]");
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        String doubleCheckAnswer = TokenConfig.getAnswer(text);
        if (doubleCheckAnswer != null && !Objects.equals(answerStr, doubleCheckAnswer)) {
            answerStr = doubleCheckAnswer;
            Log.record("检测即将提交错误的回答，已自动纠正!新回答:" + answerStr);
        }
        return answerStr;
    }

    public interface AIType {

        int TONGYI = 0;
        int GEMINI = 1;

        String[] nickNames = {"通义千问", "GEMINI"};
    }

}