package io.github.dragon813.sesame.model.normal.answerAI;

import okhttp3.*;
import org.json.JSONObject;
import io.github.dragon813.sesame.util.Log;

import java.util.List;

import static io.github.dragon813.sesame.util.JsonUtil.getValueByPath;

/**
 * GenAI帮助类
 *
 * @author Xiong
 */
public class GeminiAI implements AnswerAIInterface {
    private final String TAG = GeminiAI.class.getSimpleName();

    private final String url = "https://api.genai.gd.edu.kg/google";

    private final String token;

    // 私有构造函数，防止外部实例化
    public GeminiAI(String token) {
        if (token != null && !token.isEmpty()) {
            this.token = token;
        } else {
            this.token = "";
        }
        /*if (cUrl != null && !cUrl.isEmpty()) {
            url = cUrl.trim().replaceAll("/$", "");
        }*/
    }

    /**
     * 获取AI回答结果
     *
     * @param text 问题内容
     * @return AI回答结果
     */
    @Override
    public String getAnswerStr(String text) {
        Response response = null;
        String result = "";
        try {
            String content = "{\n" +
                    "    \"contents\": [\n" +
                    "        {\n" +
                    "            \"parts\": [\n" +
                    "                {\n" +
                    "                    \"text\": \"只回答答案 " + text + "\"\n" +
                    "                }\n" +
                    "            ]\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}";
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(content, mediaType);
            String url2 = url + "/v1beta/models/gemini-1.5-flash:generateContent?key=" + token;
            Request request = new Request.Builder()
                    .url(url2)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            response = client.newCall(request).execute();
            if (response.body() == null) {
                return result;
            }
            String json = response.body().string();
            if (!response.isSuccessful()) {
                Log.other("Gemini请求失败");
                Log.i("Gemini接口异常：" + json);
                //可能key出错了
                return result;
            }
            JSONObject jsonObject = new JSONObject(json);
            result = getValueByPath(jsonObject, "candidates.[0].content.parts.[0].text");
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            if (response != null) {
                response.close();
            }
        }
        return result;
    }

    /**
     * 获取答案
     *
     * @param title     问题
     * @param answerList 答案集合
     * @return 空没有获取到
     */
    @Override
    public Integer getAnswer(String title, List<String> answerList) {
        StringBuilder answerStr = new StringBuilder();
        for (String answer : answerList) {
            answerStr.append("[").append(answer).append("]");
        }
        String answerResult = getAnswerStr(title + "\n" + answerStr);
        if (answerResult != null && !answerResult.isEmpty()) {
            for (int i = 0, size = answerList.size(); i < size; i++) {
                if (answerResult.contains(answerList.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }
}
