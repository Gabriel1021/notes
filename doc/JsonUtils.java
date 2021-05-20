package com.zb.thor.utils;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.zb.framework.common.util.StringUtils;
import com.zb.thor.zbbonuscal.FormulaProxy;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.codehaus.jackson.type.JavaType;
import org.codehaus.jackson.type.TypeReference;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class JsonUtils {

	private static ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 对象字段全部列入
        objectMapper.setSerializationInclusion(Inclusion.NON_DEFAULT);

        // 取消默认转换timestamps形式
        objectMapper.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS,false);

        // 忽略空bean转json的错误
        objectMapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS,false);

        // 统一日期格式yyyy-MM-dd HH:mm:ss
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

        // 忽略在json字符串中存在,但是在java对象中不存在对应属性的情况
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES,false);
    }

    /**
     * Object转json字符串
     * @param obj
     * @param <T>
     * @return
     */
    public static <T> String obj2String(T obj){
        if (obj == null){
            return null;
        }
        try {
            return obj instanceof String ? (String) obj : objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            System.out.println("Parse object to String error");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Object转json字符串并格式化美化
     * @param obj
     * @param <T>
     * @return
     */
    public static <T> String obj2StringPretty(T obj){
        if (obj == null){
            return null;
        }
        try {
            return obj instanceof String ? (String) obj : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            System.out.println("Parse object to String error");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * string转object
     * @param str json字符串
     * @param clazz 被转对象class
     * @param <T>
     * @return
     */
    public static <T> T string2Obj(String str,Class<T> clazz){
        if (StringUtils.isEmpty(str) || clazz == null){
            return null;
        }
        try {
            return clazz.equals(String.class)? (T) str :objectMapper.readValue(str,clazz);
        } catch (IOException e) {
            System.out.println("Parse String to Object error");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * string转object
     * @param str json字符串
     * @param typeReference 被转对象引用类型
     * @param <T>
     * @return
     */
    public static <T> T string2Obj(String str, TypeReference<T> typeReference){
        if (StringUtils.isEmpty(str) || typeReference == null){
            return null;
        }
        try {
            return (T)(typeReference.getType().equals(String.class)? str :objectMapper.readValue(str,typeReference));
        } catch (IOException e) {
            System.out.println("Parse String to Object error");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * string转object 用于转为集合对象
     * @param str json字符串
     * @param collectionClass 被转集合class
     * @param elementClasses 被转集合中对象类型class
     * @param <T>
     * @return
     */
    public static <T> T string2Obj(String str,Class<?> collectionClass,Class<?>... elementClasses){
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(collectionClass,elementClasses);
        try {
            return objectMapper.readValue(str,javaType);
        } catch (IOException e) {
            System.out.println("Parse String to Object error");
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * string转object 用于转为集合对象
     * @param jsonStr
     * @param key
     * @return
     */
    public static String getSimpleValue(String jsonStr,String key){
        Map m = string2Obj(jsonStr,Map.class);
        try {
            return (String) m.get(key);
        } catch (Exception e) {
            System.out.println("Parse String to Object error");
            e.printStackTrace();
            return null;
        }
    }

    public static JSONObject changeJsonObj(JSONObject jsonObj, Map<String, Object> keyMap) {
        JSONObject resJson = new JSONObject();
        Set<String> keySet = jsonObj.keySet();
        for (String key : keySet) {
        	String resKey = jsonObj.getString(key);
        	Object res = null;
        	if(StringUtils.isNotEmpty(resKey)){
        		if(resKey.startsWith("@")){
        			res = keyMap.get(resKey.substring(1));
        		}else if(resKey.startsWith("{")&&resKey.endsWith("}")){
        			JSONObject jsonobj1 = jsonObj.getJSONObject(key);
        			res = changeJsonObj(jsonobj1, keyMap);
        		}else if(resKey.startsWith("[")&&resKey.endsWith("]")){
        			JSONArray jsonArr = jsonObj.getJSONArray(key);                  
        			res = changeJsonArr(jsonArr, keyMap);
        		}else{
        			res = resKey;
        		}
        		resJson.put(key.toUpperCase(), res);
        	}
//            String resKey = keyMap.get(jsonObj.getString(key).substring(1)) == null ? "" : keyMap.get(jsonObj.getString(key).substring(1)).toString();
//            try {
//                JSONObject jsonobj1 = jsonObj.getJSONObject(key);
//                resJson.put(key, changeJsonObj(jsonobj1, keyMap));
//            } catch (Exception e) {
//                try {
//                    JSONArray jsonArr = jsonObj.getJSONArray(key);
//                    resJson.put(key, changeJsonArr(jsonArr, keyMap));
//                } catch (Exception x) {
//                    resJson.put(key, res);
//                }
//            }
        }
        return resJson;
    }

    public static JSONArray changeJsonArr(JSONArray jsonArr,Map<String, Object> keyMap) {
        JSONArray resJson = new JSONArray();
        for (int i = 0; i < jsonArr.size(); i++) {
            JSONObject jsonObj = jsonArr.getJSONObject(i);
            resJson.add(changeJsonObj(jsonObj, keyMap));
        }
        return resJson;
    }

    /**
     * 去除首尾指定字符
     * @param str   字符串
     * @param element   指定字符
     * @return
     */
    public static String trimFirstAndLastChar(String str, String element){
        boolean beginIndexFlag = true;
        boolean endIndexFlag = true;
        do{
            int beginIndex = str.indexOf(element) == 0 ? 1 : 0;
            int endIndex = str.lastIndexOf(element) + 1 == str.length() ? str.lastIndexOf(element) : str.length();
            str = str.substring(beginIndex, endIndex);
            beginIndexFlag = (str.indexOf(element) == 0);
            endIndexFlag = (str.lastIndexOf(element) + 1 == str.length());
        } while (beginIndexFlag || endIndexFlag);
        return str;
    }

    public static String  formatFormula(String warningFormula, Map maps){
        String patternString = "@(" + StringUtils.join(maps.keySet(), "|") + ")";

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(warningFormula);

        //两个方法：appendReplacement, appendTail
        StringBuffer sb = new StringBuffer();
        while(matcher.find()) {
            matcher.appendReplacement(sb, String.valueOf(maps.get(matcher.group(1))));
        }
        matcher.appendTail(sb);

        //out: Garfield really needs some coffee.
        return sb.toString();
    }


    public static void main(String[] args) {
//		String user = "{\"id\":\"1\",\"name\":\"liwenlong\"}";
//		System.out.println(JsonUtils.getSimpleValue(user,"name"));
//        [{"FID":"xSCyYlWJRG6YNO8B1SbYCEXW8BM=","CFSOWNOID":"RvC/NcWVRf2v9CLywKtd7EVA25s=","CFSERVICECURPT":null},{"FID":"wdXQHbS5Tf6nNMr7+aWBcEXW8BM=","CFSOWNOID":"AtDcX7ejRMWkShUXpmyC+kVA25s=","CFSERVICECURPT":null},{"FID":"vToqwP3fSZ2iI4ph0pHniUXW8BM=","CFSOWNOID":"0MuvKFwCT5OrTPSu9VhfiUVA25s=","CFSERVICECURPT":null},{"FID":"ulc5fKWoQWihWMDxfMXw6EXW8BM=","CFSOWNOID":"Wmlz7vQsTzuTsjUM+qhejkVA25s=","CFSERVICECURPT":null},{"FID":"tZNhniQRRMuNcknsmzSw30XW8BM=","CFSOWNOID":"dfX/EVMNRqqyuqz2h6m5xEVA25s=","CFSERVICECURPT":null},{"FID":"tAgdnOVrRzya8BvHsXmQxkXW8BM=","CFSOWNOID":"jFsHTwmMQTCxsg1QEm2Eb0VA25s=","CFSERVICECURPT":null},{"FID":"sOVW8tFAT0KOzskAP/FHlUXW8BM=","CFSOWNOID":"hrjPY9qsSuicGtm/JSf3V0VA25s=","CFSERVICECURPT":null},{"FID":"s5adZQj0QOyD1uHlv2T+qkXW8BM=","CFSOWNOID":"DCPhgzooQ4mlAa07Wqy6h0VA25s=","CFSERVICECURPT":null},{"FID":"rkEwE5JRRduQf8RPRRszvUXW8BM=","CFSOWNOID":"iR4b8SyJRQqJYCl1qyGyHkVA25s=","CFSERVICECURPT":0},{"FID":"r2nsYqpMQ1msQbNkHg+VTkXW8BM=","CFSOWNOID":"lxCPVBJxSa+LIB2jetiIh0VA25s=","CFSERVICECURPT":null},{"FID":"qOkJ6Li9TXqvQjBSph45jEXW8BM=","CFSOWNOID":"+xtB4ts3SoGEJc8JEEIfGkVA25s=","CFSERVICECURPT":null},{"FID":"oaO7HUx6QUG6QXAjt9GQsUXW8BM=","CFSOWNOID":"Odl7/9AXTEK+wzZSV49RA0VA25s=","CFSERVICECURPT":null},{"FID":"nbMqVVG6SvyQg93vEMT0GUXW8BM=","CFSOWNOID":"hzzbSe37Qia2yGV/Pb/1A0VA25s=","CFSERVICECURPT":null},{"FID":"mz73JdCPRrKfpVGE4yFwgkXW8BM=","CFSOWNOID":"qoQ+MKhsT2GFDVRwikdYTUVA25s=","CFSERVICECURPT":null},{"FID":"jRLNWIakTa+yHPWiwmKehkXW8BM=","CFSOWNOID":"tb3hbGaBQROl7s5bEhxeCEVA25s=","CFSERVICECURPT":null},{"FID":"hH1iWZ0WTiqkSvZvnlTcq0XW8BM=","CFSOWNOID":"nzQb0FafQoiq5zpaqVFCUEVA25s=","CFSERVICECURPT":null},{"FID":"hGrPPmzkTPyen//4XRoiRUXW8BM=","CFSOWNOID":"jkJ1gvwDQni7t8nU41/xlkVA25s=","CFSERVICECURPT":null},{"FID":"gX/4q4/2Q1KAUEn52R3SmEXW8BM=","CFSOWNOID":"Gk23wbP6Q+OGX1yNBCNW6kVA25s=","CFSERVICECURPT":null},{"FID":"fnqHwW8mSP+zoP/24EjnjEXW8BM=","CFSOWNOID":"wLzyigCwSc6m7ryBgZH8qEVA25s=","CFSERVICECURPT":null},{"FID":"exiUcO+lT+yNoUFf/dD0WEXW8BM=","CFSOWNOID":"KYGeALNGQE6977yDCnYHIkVA25s=","CFSERVICECURPT":null},{"FID":"eFR67+RPQQ2C/uxfipiYiEXW8BM=","CFSOWNOID":"urwjQWoMRwe9n0ZEpU6RRkVA25s=","CFSERVICECURPT":null},{"FID":"duKpQaqiS7iGzEDwKpqMSkXW8BM=","CFSOWNOID":"oakte0yjRaWQQrxIHQW3iEVA25s=","CFSERVICECURPT":null},{"FID":"aK2c1wOsShm9ZnOoBfRjc0XW8BM=","CFSOWNOID":"BwKgbr3YTyCvO/Z6DFc+4EVA25s=","CFSERVICECURPT":null},{"FID":"Yq9jcDBuR6i6w2qQiHn3Y0XW8BM=","CFSOWNOID":"G/3piKwuQpqxh0LxpXOt8EVA25s=","CFSERVICECURPT":null},{"FID":"YIQlcPL4Sz2MUvfCfOvToEXW8BM=","CFSOWNOID":"7LMO/KPTSxWk/3Cg2NZJG0VA25s=","CFSERVICECURPT":null},{"FID":"XOObP5RATXaxy1nz7IhLBkXW8BM=","CFSOWNOID":"h3rAEZJKRkK+xmO8B84fh0VA25s=","CFSERVICECURPT":null},{"FID":"T0v8J4pyRXGPvvrr4gXL8EXW8BM=","CFSOWNOID":"36I4qoNiSq2W7UVOD73770VA25s=","CFSERVICECURPT":null},{"FID":"Sy+2zOg6TY2hX0/hiNFheEXW8BM=","CFSOWNOID":"TCOLkccCRsWeJgrrHxZlHkVA25s=","CFSERVICECURPT":null},{"FID":"SSQBgDZ6RRGKPVUntF9NLEXW8BM=","CFSOWNOID":"zKmC1Y9ITPyZ6O7lKWsodkVA25s=","CFSERVICECURPT":null},{"FID":"PGbr/H7qTmOZOUCs2bEK9UXW8BM=","CFSOWNOID":"Qh6eTHw5SOijGkVXI8sHmUVA25s=","CFSERVICECURPT":null},{"FID":"LqAuj/RZSW6BGQVOexROfUXW8BM=","CFSOWNOID":"n8P6VIiTQFyJrXhPMyI1YkVA25s=","CFSERVICECURPT":0},{"FID":"L132zg60T+yTJK0xREW6mUXW8BM=","CFSOWNOID":"C5TwGiSdSpeGR0G9HbwfkUVA25s=","CFSERVICECURPT":null},{"FID":"GHDxfN/KRHKt7vdsgBk9xEXW8BM=","CFSOWNOID":"v0PJYfBTTmuXD0+y4O/lw0VA25s=","CFSERVICECURPT":null},{"FID":"EyNkZLx8SF2dIyJ6ifuveUXW8BM=","CFSOWNOID":"s/sEUDQTQdmRVu0H8xSY9kVA25s=","CFSERVICECURPT":null},{"FID":"DQpY6sKYRkSLUi0t7FVIU0XW8BM=","CFSOWNOID":"JptxAQTMTjK+l3E2JSCq+kVA25s=","CFSERVICECURPT":null},{"FID":"Ac9Ams12SHKyQDJbauvMAUXW8BM=","CFSOWNOID":"0wRUpD8iQcSSKJG3oCcrh0VA25s=","CFSERVICECURPT":null},{"FID":"9qhH+wykScWGKfHRf8+ZJ0XW8BM=","CFSOWNOID":"ki/1kfd6QKyVExDY6uGaFUVA25s=","CFSERVICECURPT":null},{"FID":"8vflm7ipSpqnCrI1D7I190XW8BM=","CFSOWNOID":"Sr3ST5aLQv67L/STYwyh1EVA25s=","CFSERVICECURPT":null},{"FID":"84xVnyoDTUuo0m6nabvysEXW8BM=","CFSOWNOID":"wbIc05eWTna7+z9feauPpEVA25s=","CFSERVICECURPT":null},{"FID":"1k/JBR5gQEmhRRecYEARv0XW8BM=","CFSOWNOID":"tzK7v0jfRO+VaVNuJJT+JEVA25s=","CFSERVICECURPT":null},{"FID":"1g9ixIC3TCegtNdGFjiYiEXW8BM=","CFSOWNOID":"DhswAI+MTwuHzL+wXDGQW0VA25s=","CFSERVICECURPT":null},{"FID":"+ONMiMoxSJ2p14iS3mKFXUXW8BM=","CFSOWNOID":"vPTTucKQQSyjTjF8OaKX9UVA25s=","CFSERVICECURPT":null}]
//        String jsonStr = "{\"user\":{\"name\":\"张三\",\"sex\":\"男\",\"hobby\":[{\"motion\":\"足球\",\"desc\":\"任性\"},{\"game\":\"英雄联盟\",\"desc\":\"就是这么任性\"}]}}";

        String jsonStr = "[{\"组织\":\"@org\",\"部门\":\"@dept\"}]";
        JSONObject o = (JSONObject) JSONObject.parseArray(jsonStr).get(0);
        Map<String, Object> keyMap = new HashMap<String, Object>();
        keyMap.put("org", "1");
        keyMap.put("dept", "有关部门");
        JSONObject jsonObj = JsonUtils.changeJsonObj(o,keyMap);
        System.out.println("换值结果 》》 " + jsonObj.toString());


        String patternString = "@(" + StringUtils.join(keyMap.keySet(), "|") + ")";

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher("@org<=1.5");

        //两个方法：appendReplacement, appendTail
        StringBuffer sb = new StringBuffer();
        while(matcher.find()) {
            matcher.appendReplacement(sb, String.valueOf(keyMap.get(matcher.group(1))));
        }
        matcher.appendTail(sb);
        System.out.println(sb.toString());

        try {
            Object result = FormulaProxy.evl(null, sb.toString());
            System.out.println(JSONObject.toJSONString(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
