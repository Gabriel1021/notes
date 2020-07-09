package com.zb.index.warning.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.zb.framework.common.util.RedisUtils;
import com.zb.index.warning.common.CommonConst;
import com.zb.index.warning.common.enums.MessageType;
import com.zb.index.warning.common.enums.TaskType;
import com.zb.index.warning.entity.WarningEvent;
import com.zb.index.warning.entity.WarningEventExample;
import com.zb.index.warning.entity.WarningEventReceipt;
import com.zb.index.warning.entity.WarningHandle;
import com.zb.index.warning.entity.WarningHandleDetail;
import com.zb.index.warning.entity.WarningMessage;
import com.zb.index.warning.feign.IPushService;
import com.zb.index.warning.mapper.WarningCloseDetailMapper;
import com.zb.index.warning.mapper.WarningEventMapper;
import com.zb.index.warning.mapper.WarningEventReceiptMapper;
import com.zb.index.warning.mapper.WarningHandleDetailMapper;
import com.zb.index.warning.mapper.WarningHandleMapper;
import com.zb.index.warning.mapper.WarningMessageMapper;
import com.zb.index.warning.service.IMeasureService;
import com.zb.index.warning.service.IWarningMesgConsumer;
import com.zb.index.warning.utils.GenSerialNumberUtils;
import com.zb.index.warning.utils.PushMessageUtils;
import com.zb.index.warning.utils.TimeSwitchUtils;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * @author : zouxiaoxiang
 * @ClassName : com.zb.index.warning.service.impl.MeasureServiceImpl
 * @description :举措服务实现类
 * @date : 2020.05.11 20:59
 */
@Service("measureService")
public class MeasureServiceImpl implements IMeasureService, IWarningMesgConsumer {


  @Autowired(required = false)
  private WarningEventMapper warningEventMapper;

  @Autowired(required = false)
  private WarningEventReceiptMapper warningEventReceiptMapper;

  @Autowired(required = false)
  private WarningHandleMapper warningHandleMapper;

  @Autowired(required = false)
  private WarningHandleDetailMapper warningHandleDetailMapper;

  @Autowired(required = false)
  private WarningMessageMapper warningMessageMapper;

  @Autowired(required = false)
  private WarningCloseDetailMapper warningCloseDetailMapper;

  @Autowired(required = true)
  private RedisUtils redisUtils;

  private IPushService pushService;

  @Autowired
  public void setPushService(IPushService pushService) {
    this.pushService = pushService;
  }

  private static Logger logger = LoggerFactory.getLogger(MeasureServiceImpl.class);

  private Map<String, CopyOnWriteArrayList<TaskChild>> taskStatusMap = new ConcurrentHashMap<>();

  // key taskid value - key eventid value status

  /**
   * 生成举措单,举措数据录入
   *
   * @param taskId 任务id
   */
  @Override
  public void produceMeasure(String taskId) {
    // 完成一个task后，产生 举措  知会  预警 消息
    List<Map> groupList = warningEventMapper.selectWarningIDByTaskId(taskId);
    if (CollectionUtils.isEmpty(groupList)) {
      logger.error("任务{}没有数据或者没有归属组织", taskId);
      return;
    }
    List<String> sourcebillList = Lists.newArrayList();
    for (Map map : groupList) {
      String handler = MapUtils.getString(map, "HANDLER");
      String hanglerCode = MapUtils.getString(map, "HANDLER_CODE");
      String handlerName = MapUtils.getString(map, "HANDLER_NAME");
      String category = MapUtils.getString(map, "EVENT_CATEGORY");
      String org = MapUtils.getString(map, "ORG");
      String orgName = MapUtils.getString(map, "ORG_NAME");
      String orgLongNumber = MapUtils.getString(map, "ORG_LONG_NUMBER");
      String eventType = MapUtils.getString(map, "EVENT_TYPE");
      // 主鍵
      List<String> ids = Arrays.asList(StringUtils.split(MapUtils.getString(map, "IDS"), ","));
      WarningEventExample warningEventExample = new WarningEventExample();
      WarningEventExample.Criteria cri = warningEventExample.createCriteria();
      cri.andIdIn(ids);
      List<WarningEvent> warningEventList = warningEventMapper.selectByExample(warningEventExample);
      // 这里添加handler判断是因为warning_event做了控制当handler不为空时，才是预警举措
      if (StringUtils.isNotEmpty(handler) && !CollectionUtils.isEmpty(warningEventList)) {
        // 这一步是产生举措单和举措详情，并且发送反馈举措消息
        creatNewMeasureItems(sourcebillList, handler, handlerName, hanglerCode, category, org, orgName, orgLongNumber, eventType, warningEventList);
      }
    }
    //知会消息类型入库 知会类型定义的枚举值 = 2  接收预警 = 3
    warningMessageMapper.genMessageForNotifyApplication(TimeSwitchUtils.df.format(new Date()), taskId,
        Arrays.asList(MessageType.MESSAGE_INFORM.value, MessageType.RECEIVE_WARNING.value));

    sourcebillList.add(taskId);
    // 推送消息
    PushMessageUtils.pushMessageBysourcebillIds(sourcebillList);
  }

  /**
   * 根据申请关闭的开始和关闭结束的时间过滤出warning_event
   */
  private void filteWarningEventByCloseEndTime(String org, List<WarningEvent> warningEventList) {
    for (Iterator<WarningEvent> iterator = warningEventList.iterator(); iterator.hasNext(); ) {
      WarningEvent warningEvent = iterator.next();
      StringBuilder sb = new StringBuilder();
      String key = sb.append(CommonConst.INDEX_WARNING_FLAG).append(org).append(":").append(warningEvent.getEventId()).toString();
      if (StringUtils.isEmpty(redisUtils.getStr(key))) {
        // 查詢數據庫
        List<Map> ls = warningCloseDetailMapper.selectAllCloseDetailEndTime(org, warningEvent.getEventId());
        if (!CollectionUtils.isEmpty(ls)) {
          // 放入緩存
          setStartTimeAndEndTimeRedis(key, ls, iterator);
        }
      } else {
        String timeStr = redisUtils.getStr(key);
        List<String> times = Arrays.asList(timeStr.split("-"));
        if (!CollectionUtils.isEmpty(times) && times.size() == 2) {
          String startTimeStr = times.get(0);
          String endTimeStr = times.get(1);
          if (StringUtils.isNotEmpty(startTimeStr) && StringUtils.isNotEmpty(endTimeStr)) {
            long startTimeL = Long.valueOf(startTimeStr);
            long endTimeL = Long.valueOf(endTimeStr);
            if (endTimeL > System.currentTimeMillis() && System.currentTimeMillis() > startTimeL) {
              logger.info("有关闭申请举措，该事件在时间段内不生成举措单{}", JSON.toJSON(warningEvent));
              iterator.remove();
            }
          }
        }
      }
    }
  }


  @Override
  public void produceMeasureByeventId(List<String> eventList) {
    if (CollectionUtils.isEmpty(eventList)) {
      return;
    }
    WarningEventExample warningEventExample = new WarningEventExample();
    WarningEventExample.Criteria cri = warningEventExample.createCriteria();
    cri.andIdIn(eventList);
    // 调用这个接口的话，则是同一类行的数据 人,组织，预警类型
    List<WarningEvent> list = warningEventMapper.selectByExample(warningEventExample);
    if (CollectionUtils.isEmpty(list)) {
      logger.info("查询出来的举措元数据为空");
      return;
    }
    List<String> sourcebillList = Lists.newArrayList();
    WarningEvent warningEvent = list.get(0);
    String handler = warningEvent.getHandler();
    String handlerCode = warningEvent.getHandlerCode();
    String handlerName = warningEvent.getHandlerName();
    String category = warningEvent.getEventCategory();
    String org = warningEvent.getOrg();
    String orgName = warningEvent.getOrgName();
    String orgLongNumber = warningEvent.getOrgLongNumber();
    String eventType = warningEvent.getEventType();
    creatNewMeasureItems(sourcebillList, handler, handlerName, handlerCode, category, org, orgName, orgLongNumber, eventType, list);
    PushMessageUtils.pushMessageBysourcebillIds(sourcebillList);
  }

  /**
   * 消费预警消息
   *
   * @param mesasage 消息内容
   * @param type 消息类型判断
   */
  @Override
  public void receive(String mesasage, int type) {
    // 预警消息处理   RECEIVE(0),END(1);
    JSONObject messageObj = JSON.parseObject(mesasage);
    if (messageObj.isEmpty()) {
      return;
    }
    if (TaskType.RECEIVE.ordinal() == type) {
      //数据转换 装载
//      logger.info("messageObj：" + "|||||||||接收的内容是：" + messageObj);
      // 兼容v1.0版本  v3.0版本
//      boolean versionFlag = true;
//      if(versionFlag){
      ConvertAndLoadingEarlyWarningMessage(messageObj);
//      }else{
//        logger.info("receiver message begin.......");
//        Map values = JsonUtils.string2Obj(mesasage, Map.class);
//        String number = (String) values.get("fNumber");
//        String warningName = (String) values.get("fName");
//        List<Map> list = (List<Map>) values.get("dataArray");
//        ConvertAndLoadingOldEarlyWarningMessage(number, warningName, list);
//      }
    } else if (TaskType.END.ordinal() == type) {
      // 一个taskid的任务做完，开始生成举措单和消息单生成
      String flag = messageObj.getString("flag");
      String taskId = messageObj.getString("job");
      int total = messageObj.getIntValue("total");
      try {
        if (CommonConst.SUCCESS_FLAG.equals(flag)) {
          logger.info("开始检测任务是否完成");
          // 持续5分钟的侦测 间隔10秒一次
          for (int i = 30; i > 0; i--) {
            // 每次都重新取
            List<TaskChild> taskChildren = taskStatusMap.get(taskId);
            if (CollectionUtils.isEmpty(taskChildren)) {
              logger.error("没有子任务等待10秒{}", taskId);
              sleep(taskId);
              if(CollectionUtils.isEmpty(taskStatusMap.get(taskId))){
                // 有的任务就是空跑
                logger.error("10秒后此任务任然等来没有子任务{}", taskId);
                return;
              }
              // 消耗一次等待时间
              continue;
            }
            List<TaskChild> finishTasks = taskChildren.stream()
                .filter(taskChild -> !Objects.isNull(taskChild) && ((taskChild.getStatusEnum().ordinal() == 1) || (taskChild.getStatusEnum().ordinal()
                    == 2))).collect(Collectors.toList());
            logger.info("总任务数量={},目前接收到的任务数量={},目前完成任务的数量={}", total, taskChildren.size(),
                !CollectionUtils.isEmpty(finishTasks) ? finishTasks.size() : "没有任务");
            if (!CollectionUtils.isEmpty(finishTasks)
                && (taskChildren.size() == total)
                && (taskChildren.size() == finishTasks.size())) {
              produceMeasure(taskId);
              logger.info("任务顺利完成jobId={}", taskId);
              return;
            } else {
              if (i == 1) {
                continue;
              }
              sleep(taskId);
            }
          }
          // 如果三次都没有等到任务完成，则用当前录入的取产生举措单和发送消息
          logger.error("策略等待任务完成时间超过5分钟就直接执行现有已完成的任务,taskID={}", taskId);
          produceMeasure(taskId);
        } else {
          logger.error("thor端任务异常jobId={}", taskId);
        }
      } catch (Exception e) {
        logger.error("index_warning端任务异常jobId={}", taskId);
      } finally {
        taskStatusMap.remove(taskId);
      }
    }
  }

  private void ConvertAndLoadingOldEarlyWarningMessage(String number, String warningName, List<Map> list) {
    if ("yzdcl".equals(number) || "pzdcl".equals(number) || "tmdcl".equals(number)) {
      this.addWeekWarning(list, number, warningName);
    }
//    else if ("hbzswl".equals(number) || "tmswl".equals(number) || "ydswl".equals(number)) {
//      this.addMonthDeathWarning(list, number, warningName);
//    } else if ("dayswl".equals(number)) {
//      this.addDayDeathWarning(list, number, warningName);
//    }else if("TASKDELAYPUSH".equals(number)){
//      this.taskDelayPush(list);
//    }else if("130swl".equals(number)){
//      this.add130DeathWarning(list, number, warningName);
//    }else if("28syl".equals(number) || "35syl".equals(number)){
//      this.addMonthWarning(list, number, warningName);
//    }
    logger.info("receiver old message end.......");
  }


  /**
   * 旧数据的eventDetail是存分场信息
   */
  public void addWeekWarning(List<Map> list, String type, String indexName) {
    if (list == null || list.size() == 0) {
      return;
    }
    List<WarningEvent> warningList = new ArrayList<>();
    List<Map> pushList = new ArrayList<>();
    List<WarningMessage> pushLog = new ArrayList<>();
    String standardStr = (String)list.get(0).get("standard");
    String now = TimeSwitchUtils.df2.format(new Date());
    BigDecimal standard = StringUtils.isNotEmpty(standardStr) ? (new BigDecimal(standardStr)) : null;
    String title = indexName;
    StringBuilder contentBuilder = new StringBuilder(now);
    contentBuilder.append("“").append(title).append("”指标发生预警，请填写原因分析及举措！");
    String content = contentBuilder.toString();
    String taskId = com.zb.framework.common.util.StringUtils.getUUID();
    for (Map m : list) {
      String monthRate = (String)m.get("monthRate");
      if (com.zb.framework.common.util.StringUtils.isEmpty(monthRate)) {
        continue;
      }

      String orgId = (String)m.get("orgId");
//			String standard = (String) m.get("standard");

      String orgLongNumber = (String)m.get("orgLongNumber");
      String orgName = (String)m.get("orgName");
      String leaderName = (String)m.get("lname");
      String leaderNumber = (String)m.get("lnumber");
      String weekRate = (String)m.get("weekRate");
      String planValue = (String)m.get("planValue");
      String monthPart = (String)m.get("monthPart");
      String weekPart = (String)m.get("weekPart");
      WarningEvent w = new WarningEvent();
      w.setId(com.zb.framework.common.util.StringUtils.getUUID());
      w.setOrg(orgId);
      w.setOrgLongNumber(orgLongNumber);
      w.setOrgName(orgName);
      // 指标编码  TODO 这里取值不对 字段涉及到的地方挺多
      w.setEventId("");
      w.setEventCode(type);
      w.setEventName(indexName);
      w.setStandardValue(standard);
      w.setActualValue(new BigDecimal(monthRate));
      w.setWeekValue(StringUtils.isNotEmpty(weekRate) ? new BigDecimal(weekRate) : null);
      w.setPlanValue(StringUtils.isNotEmpty(planValue) ? new BigDecimal(planValue) : null);
      w.setBusiDate(now);
      w.setCreateTime(now);
      // 假设的taskid
      w.setTaskid(taskId);
      w.setEventCategory(getTagCode(type));
      w.setEventType("1");

      // 审批状态 v3.0event表暂时没有存值
//      w.setStatus("1");
      // TODO 处理人id暂时获取不到
      w.setHandler(com.zb.framework.common.util.StringUtils.getUUID());
      w.setHandlerCode(leaderNumber);
      w.setHandlerName(leaderName);
      warningList.add(w);
    }

    if (warningList != null && warningList.size() > 0) {
      warningEventMapper.insertBatch(warningList);
      // 一个task任务中用（组织+人+预警类型+预警事件分类）来分组
//    Map<String,Map<String,Map<String,Map<String,List<WarningEvent>>>>> OrgAndPersonAndCategoryAndEventTypeMap = new HashMap<>();
      Map<String, Map<String, Map<String, Map<String, List<WarningEvent>>>>> OrgMap = new HashMap<>();
      for (WarningEvent warningEvent : warningList) {
        String org = warningEvent.getOrg();
        if (!OrgMap.containsKey(org)) {
          OrgMap.put(org, new HashMap<>());
        }
        Map<String, Map<String, Map<String, List<WarningEvent>>>> personMap = OrgMap.get(org);
        String handler = warningEvent.getHandler();
        if (!personMap.containsKey(handler)) {
          personMap.put(handler, new HashMap<>());
        }
        String category = warningEvent.getEventCategory();
        Map<String, Map<String, List<WarningEvent>>> categoryMap = personMap.get(handler);
        if (!categoryMap.containsKey(category)) {
          categoryMap.put(category, new HashMap<>());
        }
        String eventType = warningEvent.getEventType();
        Map<String, List<WarningEvent>> eventTypeMap = categoryMap.get(category);
        if (!eventTypeMap.containsKey(eventType)) {
          eventTypeMap.put(eventType, new ArrayList<>());
        }
        List<WarningEvent> warningEvents = eventTypeMap.get(eventType);
        warningEvents.add(warningEvent);
      }

      for (Map.Entry<String, Map<String, Map<String, Map<String, List<WarningEvent>>>>> OrgMapEntry : OrgMap.entrySet()) {
        String org = OrgMapEntry.getKey();
        Map<String, Map<String, Map<String, List<WarningEvent>>>> personMap = OrgMap.get(org);
        for (Map.Entry<String, Map<String, Map<String, List<WarningEvent>>>> personMapEntry : personMap.entrySet()) {
          String person = personMapEntry.getKey();
          Map<String, Map<String, List<WarningEvent>>> categoryMap = personMap.get(person);
          for (Map.Entry<String, Map<String, List<WarningEvent>>> categoryMapEntry : categoryMap.entrySet()) {
            String categary = categoryMapEntry.getKey();
            Map<String, List<WarningEvent>> eventTypeMap = categoryMap.get(categary);
            for (Map.Entry<String, List<WarningEvent>> eventTypeMapEntry : eventTypeMap.entrySet()) {
              String eventType = eventTypeMapEntry.getKey();
              List<WarningEvent> warningEvents = eventTypeMap.get(eventType);

              String measureID = getUUIDStr();
              WarningHandle warningHandle = new WarningHandle();
              warningHandle.setId(measureID);
              warningHandle.setName(generateMeasureName(warningEvents)); // 命名规则
              warningHandle.setCode(GenSerialNumberUtils.getWarningHandleCode(CommonConst.BUS_TYPE)); // 编码规则
              warningHandle.setWarningCategory(categary);
              warningHandle.setOrg(org);
              warningHandle.setOrgLongnumber(warningEvents.get(0).getOrgLongNumber());
              warningHandle.setOrgName(warningEvents.get(0).getOrgName());
              //设置日期格式
              String date = TimeSwitchUtils.df.format(new Date());
              warningHandle.setTs(date);  // 当前时间
              warningHandle.setWarningType(eventType);
              warningHandle.setCreator(person);//处理人id
              warningHandle.setCreatorCode(warningEvents.get(0).getHandlerCode()); // 处理人账号
              warningHandle.setCreatorName(warningEvents.get(0).getHandlerName()); //处理人名称
              warningHandle.setCreateTime(TimeSwitchUtils.df.format(new Date()));
              // 产生举措单
              warningHandleMapper.insert(warningHandle);
              List<WarningHandleDetail> handleList = new ArrayList<>();
              for (WarningEvent warningEvent : warningEvents) {
                WarningHandleDetail warningHandleDetail = new WarningHandleDetail();
                // 初始化举措数据时  举措 审批人 审批人名称 审批日期 审批意见  定义审批状态
                warningHandleDetail.setId(getUUIDStr());
                warningHandleDetail.setPid(measureID);
                warningHandleDetail.setEventId(warningEvent.getEventId());
                warningHandleDetail.setCreator(person);
                warningHandleDetail.setCreatorCode(warningEvent.getHandlerCode());
                warningHandleDetail.setCreatorName(warningEvent.getHandlerName());
                warningHandleDetail.setCreateTime(TimeSwitchUtils.df.format(new Date()));
                warningHandleDetail.setStatus("0");
                warningHandleDetail.setTs(TimeSwitchUtils.df.format(new Date()));
                handleList.add(warningHandleDetail);
              }
              // 批量录入
              warningHandleDetailMapper.insertBatch(handleList);

              // 推送消息
              // 一条event一条消息
//              if (StringUtils.isNotEmpty(leaderNumber)) {
//                WarningMessage wm = new  WarningMessage();
//                wm.setId(com.zb.framework.common.util.StringUtils.getUUID());
//                // TODO  之前存的是预警 WarningEvent主键 需要产生举措单
////              wm.setWarningId(w.getId());
//                wm.setSourcebill("");
//                wm.setSourcebillType("0");
//                wm.setTitle(title);
//                wm.setCreator("thorOs");
//                wm.setCreateTime(TimeSwitchUtils.df.format(new Date()));
//                wm.setMessageType(MessageType.FEEDBACK_MEASURE.value);
//                wm.setWarningType("0");
//                wm.setContent(content);
//                wm.setReceiver(leaderName);
//                wm.setReceiverNumber(leaderNumber);
//                wm.setStatus("0");
//                wm.setWarningType("1");
//                wm.setCategory(getTagCode(type));
//                wm.setTs(TimeSwitchUtils.df.format(new Date()));
//                pushLog.add(wm);
//                pushList.add(this.createPushMap(title, content,wm.getId(), leaderNumber, "4", w.getId(), "submit",type,PushMessageUtils
// .ORDERID_IN));
//              }
            }
          }
        }
      }
    }

    if (pushLog != null && pushLog.size() > 0) {
      try {
        pushService.pushMoreDiff(pushList);
      } catch (Exception e) {
        e.printStackTrace();
      }
      warningMessageMapper.insertBatch(pushLog);
    }
  }

  private Map createPushMap(String title, String content, String contentId, String receiver, String pushModel, String warningId, String type,
      String indexCode, String orderid) {
    StringBuilder payload = new StringBuilder(title);
    payload.append("#").append(content).append("#").append(type).append("_").append(warningId);
    Map<String, String> s = new HashMap<String, String>();
    s.put("title", title);
    s.put("content", content);
    s.put("receiver", receiver);
    s.put("traget", PushMessageUtils.pushTarget);
    s.put("tragetname", PushMessageUtils.pushTargetName);
    s.put("payload", payload.toString());
    s.put("pushModel", pushModel);
    s.put("contentId", contentId);
    s.put("tag", this.getTagCode(indexCode));
    s.put("orderid", orderid);
    return s;
  }


  public String getTagCode(String indexCode) {
    switch (indexCode) {
      case "dayswl":
        return "002";
      case "hbzswl":
        return "001";
      case "pzdcl":
        return "001";
      case "tmdcl":
        return "001";
      case "tmswl":
        return "001";
      case "ydswl":
        return "002";
      case "yzdcl":
        return "001";
      case "28syl":
        return "001";
      case "35syl":
        return "001";
      default:
        return "";
    }
  }

  private void sleep(String taskId) {
    try {
      logger.info("等待任务jobId={}", taskId);
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
      logger.error("线程睡眠异常{}", e.getMessage());
    }
  }

  /**
   * 预警数据的转换和装载
   */
  private void ConvertAndLoadingEarlyWarningMessage(JSONObject messageObj) {
    // 接收任务的任务的状态 0 初始化  1 执行中  2 正常执行完成任务  3异常结束
    JSONArray warningEventReceiptArry = messageObj.getJSONArray("warningEventReceiptList");
    JSONObject warningEventObj = messageObj.getJSONObject("warningEvent");
    if (Objects.isNull(warningEventReceiptArry) || warningEventReceiptArry.isEmpty()) {
      logger.info("没有任何接收人,但可以继续执行！");
    }
    if (Objects.isNull(warningEventObj)) {
      logger.error("可预警的事件为空，不能继续向下执行！");
      return;
    }
    JSONObject dataObject = warningEventObj.getJSONObject("data");
    String warningEventId = getUUIDStr();
    String taskId = warningEventObj.getString("job");
    CopyOnWriteArrayList<TaskChild> taskChildren = taskStatusMap.get(taskId);
    if (CollectionUtils.isEmpty(taskChildren)) {
      taskChildren = new CopyOnWriteArrayList<>();
      taskStatusMap.put(taskId, taskChildren);
    }
    TaskChild taskChild = new TaskChild(warningEventId, StatusEnum.IN_PROGRESS);
    taskChildren.add(taskChild);
    Map<String, WarningEventReceipt> earlyTypeMap = new HashMap<>(1);
    parseWarningEventReceiptList(warningEventReceiptArry, warningEventId, earlyTypeMap);
    parseWarningEvent(warningEventObj, dataObject, warningEventId, earlyTypeMap);
    taskChild.setStatusEnum(StatusEnum.SUCCESS_END);
  }

  private void parseWarningEvent(JSONObject warningEventObj, JSONObject dataObject, String warningEventId,
      Map<String, WarningEventReceipt> earlyTypeMap) {
    if (!dataObject.isEmpty()) {
      String org = dataObject.getString("org_id");
      String orgName = dataObject.getString("org_name");
      String orgLongNumber = dataObject.getString("org_longnum");
      String actomId = dataObject.getString("actom_id");
      String actomName = dataObject.getString("actom_name");
      String actomCode = dataObject.getString("actom_code");
      String busiDate = dataObject.getString("busi_date");
      String eventDetail = dataObject.getString("event_detail");
      String sourceDataId = dataObject.getString("source_data_id");
      // 实际值的 分子 分母
      BigDecimal actFz = dataObject.getObject("act_fz", BigDecimal.class);
      BigDecimal actFm = dataObject.getObject("act_fm", BigDecimal.class);

      // 指标编码例如 001 引种达成率
      String indexCode = dataObject.getString("index_code");

      // 决定取值类型 0计划  1 定额
      String valueType = warningEventObj.getString("indexdef_type");
      // 少了 周值 和 标准值
      BigDecimal actualValue = dataObject.getObject("act", BigDecimal.class);
      BigDecimal planValue = dataObject.getObject("plan", BigDecimal.class);
      BigDecimal quotaValue = warningEventObj.getObject("quota_value", BigDecimal.class);

      BigDecimal bugetValue = dataObject.getObject("buget", BigDecimal.class);

      BigDecimal deviation = null;
      // 决定取值类型 0计划  1 定额
      if (!Objects.isNull(planValue) && !Objects.isNull(actualValue) && "0".equals(valueType)) {
        deviation = actualValue.subtract(planValue);
        logger.info(planValue + "计划偏差值：" + deviation.toString());
      } else if (!Objects.isNull(quotaValue) && !Objects.isNull(actualValue) && "1".equals(valueType)) {
        deviation = actualValue.subtract(quotaValue);
        logger.info(quotaValue + "额定偏差值：" + deviation.toString());
      }
      String taskId = warningEventObj.getString("job");
      String eventCategory = warningEventObj.getString("category");
      //明细
      String eventCategory2 = warningEventObj.getString("category2");
      String eventId = warningEventObj.getString("id");
      String eventCode = warningEventObj.getString("code");
      String eventType = warningEventObj.getString("type");
      String eventName = warningEventObj.getString("name");
      String warningLevel = warningEventObj.getString("event_level");
      String displayFormat = warningEventObj.getString("display_format");
      String sourceDataSetId = warningEventObj.getString("source_dataset");
      String displayMask = warningEventObj.getString("display_mask");

      WarningEvent warningEvent = new WarningEvent();
      warningEvent.setId(warningEventId);
      warningEvent.setOrg(org);
      warningEvent.setOrgName(orgName);
      warningEvent.setOrgLongNumber(orgLongNumber);
      warningEvent.setEventId(eventId);
      warningEvent.setEventCode(eventCode);
      warningEvent.setEventCategory(eventCategory);
      warningEvent.setEventCategory2(eventCategory2);
      warningEvent.setEventType(eventType);
      warningEvent.setEventName(eventName);
      warningEvent.setDeviation(deviation);
      warningEvent.setActFz(actFz);
      warningEvent.setActFm(actFm);
      warningEvent.setDisplayMask(displayMask);
      // 目前缺少 描述 标准值  周值
//      warningEvent.setEventDesc(eventDesc);
      warningEvent.setBusiDate(busiDate);
      warningEvent.setCreateTime(TimeSwitchUtils.df.format(new Date()));
//      warningEvent.setStandardValue(standardValue);
//      warningEvent.setWeekValue(weekValue);
      warningEvent.setValueType(valueType);
      warningEvent.setPlanValue(planValue);
      warningEvent.setActualValue(actualValue);
      warningEvent.setBugetValue(bugetValue);
      warningEvent.setQuotaValue(quotaValue);
      warningEvent.setDisplayFormat(displayFormat);
      warningEvent.setEventDetail(eventDetail);
      warningEvent.setSourceDataId(sourceDataId);
      warningEvent.setSourceDataSetId(sourceDataSetId);
      WarningEventReceipt warningEventReceipt = earlyTypeMap.get("earlyMessageHandler");
      // 一次任务重有messageType = 1 的事件 才会在 warning_event 存放handle信息
      if (!Objects.isNull(warningEventReceipt)) {
        String handler = warningEventReceipt.getReceiver();
        String handlerCode = warningEventReceipt.getReceiverNumber();
        String handlerName = warningEventReceipt.getReceiverName();
        warningEvent.setHandler(handler);
        warningEvent.setHandlerCode(handlerCode);
        warningEvent.setHandlerName(handlerName);
      }
      warningEvent.setTaskid(taskId);
      warningEvent.setActomId(actomId);
      warningEvent.setActomName(actomName);
      warningEvent.setActomCode(actomCode);
      warningEvent.setWarningLevel(warningLevel);
      warningEventMapper.insert(warningEvent);
    }
  }

  private void parseWarningEventReceiptList(JSONArray warningEventReceiptArry, String warningEventId, Map<String, WarningEventReceipt> earlyTypeMap) {
    if (!warningEventReceiptArry.isEmpty()) {
      List<WarningEventReceipt> warningEventReceiptList = Lists.newArrayList();
      List<List<Map<String, Object>>> lists = JSONObject.toJavaObject(warningEventReceiptArry, List.class);
      // 可能一个兼职多个岗位，需要去重推送消息
      HashSet<String> receiverSet = new HashSet<>();
      for (List<Map<String, Object>> list : lists) {
        for (Map<String, Object> person : list) {
          String receiver = MapUtils.getString(person, "RECEIVER");
          if (receiverSet.contains(receiver)) {
            continue;
          } else {
            receiverSet.add(receiver);
          }
          String receiverName = MapUtils.getString(person, "RECEIVERNAME");
          String receiverNumber = MapUtils.getString(person, "RECEIVERNUMBER");
          String messageType = MapUtils.getString(person, "HANDLETYPE");
          String messageId = getUUIDStr();
          WarningEventReceipt warningEventReceipt = new WarningEventReceipt();
          warningEventReceipt.setId(messageId);
          warningEventReceipt.setPid(warningEventId);
          warningEventReceipt.setReceiver(receiver);
          warningEventReceipt.setReceiverName(receiverName);
          warningEventReceipt.setReceiverNumber(receiverNumber);
          warningEventReceipt.setMessageType(messageType);
          if (MessageType.FEEDBACK_MEASURE.value.equals(messageType)) {
            earlyTypeMap.put("earlyMessageHandler", warningEventReceipt);
          }
          warningEventReceiptList.add(warningEventReceipt);
        }
      }
      warningEventReceiptMapper.insertBatch(warningEventReceiptList);
    }
  }

  private String getUUIDStr() {
    return UUID.randomUUID().toString().replace("-", "");
  }


  private String generateMeasureName(List<WarningEvent> warningEventList) {
    if (CollectionUtils.isEmpty(warningEventList)) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    List<String> eventNames = warningEventList.stream()
        .filter(warningEvent -> !Objects.isNull(warningEvent))
        .map(warningEvent -> warningEvent.getEventName())
        .collect(Collectors.toList());
    if (CollectionUtils.isEmpty(eventNames)) {
      return "";
    }
    String eventName1 = eventNames.get(0);
    if (eventNames.size() >= 2) {
      String eventName2 = eventNames.get(1);
      if (!eventName1.equals(eventName2)) {
        return sb.append(eventName1).append(",").append(eventName2).append("等预警的举措单").toString();
      }
    }
    return sb.append(eventName1).append("等预警的举措单").toString();
  }

  private void creatNewMeasureItems(List<String> sourcebillList, String handler, String handlerName, String hanglerCode,
      String category, String org, String orgName, String orgLongNumber, String eventType, List<WarningEvent> warningEventList) {
    // 通过关闭申请的时间过滤符合条件的数据
    filteWarningEventByCloseEndTime(org, warningEventList);
    if (CollectionUtils.isEmpty(warningEventList)) {
      logger.error("没有符合生成举措的预警事件");
      return;
    }
    // 业务方案 如果一个taskid对应的人和组织相同的情况下的操作如下
    String measureID = getUUIDStr();
    WarningHandle warningHandle = new WarningHandle();
    warningHandle.setId(measureID);
    warningHandle.setName(generateMeasureName(warningEventList)); // 命名规则
    warningHandle.setCode(GenSerialNumberUtils.getWarningHandleCode(CommonConst.BUS_TYPE)); // 编码规则
    warningHandle.setWarningCategory(category);
    warningHandle.setOrg(org);
    warningHandle.setOrgLongnumber(orgLongNumber);
    warningHandle.setOrgName(orgName);
    //设置日期格式
    String date = TimeSwitchUtils.df.format(new Date());
    warningHandle.setTs(date);  // 当前时间
    warningHandle.setWarningType(eventType);
    warningHandle.setCreator(handler);//处理人id
    warningHandle.setCreatorCode(hanglerCode); // 处理人账号
    warningHandle.setCreatorName(handlerName); //处理人名称
    warningHandle.setCreateTime(TimeSwitchUtils.df.format(new Date()));
    // 产生举措单
    warningHandleMapper.insert(warningHandle);

    // 产生举措消息数据 反馈举措定义的枚举值 = 1
    List<String> evnetIds = warningEventList.stream().map(warningEvent -> warningEvent.getId()).collect(Collectors.toList());
    warningMessageMapper
        .genMessageForMeasureApplication(TimeSwitchUtils.df.format(new Date()), MessageType.FEEDBACK_MEASURE.value, measureID, evnetIds);
    // 单据id,为后面统一查询发送消息
    sourcebillList.add(measureID);
    // 生成 举措详情 warningHandleDetail
    List<WarningHandleDetail> list = new ArrayList<>();
    for (String eventId : evnetIds) {
      WarningHandleDetail warningHandleDetail = new WarningHandleDetail();
      // 初始化举措数据时  举措 审批人 审批人名称 审批日期 审批意见  定义审批状态
      warningHandleDetail.setId(getUUIDStr());
      warningHandleDetail.setPid(measureID);
      warningHandleDetail.setEventId(eventId);
      warningHandleDetail.setCreator(handler);
      warningHandleDetail.setCreatorCode(hanglerCode);
      warningHandleDetail.setCreatorName(handlerName);
      warningHandleDetail.setCreateTime(TimeSwitchUtils.df.format(new Date()));
      warningHandleDetail.setStatus("0");
      warningHandleDetail.setTs(TimeSwitchUtils.df.format(new Date()));
      list.add(warningHandleDetail);
    }
    // 批量录入
    warningHandleDetailMapper.insertBatch(list);
  }

  /**
   *
   * @param closeList
   * @param iterator
   */
  private void setStartTimeAndEndTimeRedis(String key, List<Map> closeList, Iterator<WarningEvent> iterator) {
    logger.info("设置关闭申请时间段不生成举措单key值：" + key);
    if (!CollectionUtils.isEmpty(closeList)) {
      for (Map<String, Object> closeMap : closeList) {
        String startTimes = MapUtils.getString(closeMap, "starttimes");
        String endTimes = MapUtils.getString(closeMap, "endtimes");
        if (StringUtils.isNotEmpty(endTimes) && StringUtils.isNotEmpty(startTimes)) {

          List<Long> endTl = switchTime(endTimes, CommonConst.END_ONE_DAY_TIME);
          long maxTime = Collections.max(endTl).longValue();

          List<Long> startTl = switchTime(startTimes, CommonConst.START_ONE_DAY_TIME);
          long minTime = Collections.min(startTl).longValue();

          // key value 的组成方式(INDEX:WARNING:组织:事件id, 开始时间 "-" 结束时间)
          String value = String.valueOf(minTime) + "-" + String.valueOf(maxTime);
          // 设置10分钟过期
          redisUtils.setStr(key, value, 600);
          logger.info("设置关闭申请时间段不生成举措单key值{}value值{}", key, value);
          if (maxTime > System.currentTimeMillis() && System.currentTimeMillis() > minTime) {
            iterator.remove();
            logger.info("设置关闭申请时间段不生成举措单,移除当前预警事件");
          }
        }
      }
    }
  }

  private List<Long> switchTime(String startTimes, String s2) {
    List<String> startTimeList = Arrays.asList(startTimes.split(","));
    return startTimeList.stream().map(s -> {
      try {
        String ss = s + s2;
        return TimeSwitchUtils.df.parse(ss).getTime();
      } catch (ParseException e) {
        logger.error("时间转化异常{}", e.getMessage());
        return 0l;
      }
    }).collect(Collectors.toList());
  }

  public class TaskChild {

    String eventId;
    // 接收任务的任务的状态 1 执行中  2 正常执行完成任务  3异常结束
    StatusEnum statusEnum;

    public TaskChild(String eventId, StatusEnum statusEnum) {
      this.eventId = eventId;
      this.statusEnum = statusEnum;
    }

    public String getEventId() {
      return eventId;
    }

    public void setEventId(String eventId) {
      this.eventId = eventId;
    }

    public StatusEnum getStatusEnum() {
      return statusEnum;
    }

    public void setStatusEnum(StatusEnum statusEnum) {
      this.statusEnum = statusEnum;
    }
  }

  public enum StatusEnum {
    IN_PROGRESS, SUCCESS_END, EXCEPTION_END
  }
}
