package io.github.dragon813.sesame.model.normal.answerAI;

import java.util.List;

public interface AnswerAIInterface {

    /**
     * 获取AI回答结果
     *
     * @param text 问题内容
     * @return AI回答结果
     */
    String getAnswerStr(String text);

    /**
     * 获取AI答案
     */
    Integer getAnswer(String title, List<String> answerList);

    static AnswerAIInterface getInstance() {
        return new AnswerAIInterface() {
            @Override
            public String getAnswerStr(String text) {
                return "";
            }

            @Override
            public Integer getAnswer(String title, List<String> answerList) {
                return -1;
            }
        };
    }
}
