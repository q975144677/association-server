package com.association.admin.controller;

import com.alibaba.fastjson.JSONObject;
import com.association.admin.component.RedisUtil;
import com.association.workflow.condition.ConditionForActivity;
import com.association.workflow.condition.ConditionForApprove;
import com.association.workflow.condition.ConditionForAssociation;
import com.association.workflow.condition.ConditionForAssociationUser;
import com.association.workflow.enumerations.EnumForApproveStatus;
import com.association.workflow.enumerations.EnumForApproveType;
import com.association.workflow.iface.ActivityIface;
import com.association.workflow.iface.ApproveIface;
import com.association.workflow.iface.AssociationIface;
import com.association.workflow.model.ActivityDO;
import com.association.workflow.model.ApproveDO;
import com.association.workflow.model.AssociationDO;
import com.association.user.Iface.UserIface;
import com.association.workflow.model.AssociationUserDO;
import component.BasicComponent;
import component.HttpRequestException;
import component.PaginProto;
import component.Proto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mapping.Association;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.stream.Collectors;

@RestController
@RequestMapping("main")
public class MainController extends BasicComponent {
    @Autowired
    ActivityIface activityIface;
    @Autowired
    AssociationIface associationIface;
    @Autowired
    ApproveIface approveIface;
    @Autowired
    RedisUtil redis;
    @Autowired
    UserIface userIface;

    public static final int time = 5 * 60;

    @PostMapping("activities")
    public Proto<?> activities(@RequestBody ConditionForActivity condition) {
        return getResult(activityIface.queryActivity(condition));
    }

    @PostMapping("activityUsers")
    public Proto<?> activityUsers(@RequestBody ConditionForActivity condition) {
        return getResult(activityIface.queryActivityUsers(condition));
    }

    @PostMapping("associations")
    public Proto<?> associations(@RequestBody ConditionForAssociation condition) {
        return getResult(associationIface.queryAssociation(condition));
    }

    @PostMapping("associationUsers")
    public Proto<?> associationUsers(@RequestBody ConditionForAssociation condition) {
        return getResult(associationIface.getAssociationMates(condition));
    }

    @PostMapping("createNewActivity")
    public Proto<?> createNewActivity(@RequestBody ActivityDO activityDO, HttpServletRequest request) {

        Operator operator = getOperator(request);
        activityDO.setUpdateUserGuid(operator.getGuid());
        activityDO.setUpdateUserName(operator.getName());
        activityDO.setCreateUserGuid(operator.getGuid());
        activityDO.setCreateUserName(operator.getName());
        return getResult(activityIface.createNewActivity(activityDO));
    }

    @PostMapping("updateActivity")
    public Proto<?> updateActivity(@RequestBody ActivityDO activityDO, HttpServletRequest request) {
        if (StringUtils.isEmpty(activityDO.getGuid())) {
            throw new HttpRequestException(HttpRequestException.PARAM_ERROR, HttpStatus.OK.value());
        }
        Operator operator = getOperator(request);
        activityDO.setUpdateUserGuid(operator.getGuid());
        activityDO.setUpdateUserName(operator.getName());
        return getResult(activityDO);
    }

    @PostMapping("createAssociation")
    public Proto<?> createAssociation(@RequestBody AssociationDO associationDO, HttpServletRequest request) {

        Operator operator = getOperator(request);
        associationDO.setCreateUserGuid(operator.getGuid());
        associationDO.setCreateUserName(operator.getName());
        return getResult(associationIface.createNewAssociation(associationDO));
    }

    //创建社团 approveType 传 5
    @PostMapping("addApprove")
    public Proto<Boolean> addApprove(@RequestBody ApproveDO approveDO, HttpServletRequest request) {
        System.out.println("****************************" + JSONObject.toJSONString(approveDO));
        Operator operator = getOperator(request);
        approveDO.setCreateUserGuid(operator.getGuid());
        approveDO.setCreateUserName(operator.getName());
        approveDO.setApproveStatus(EnumForApproveStatus.START.getCode());
        approveDO.setSchoolId(operator.getSchoolId());
        approveDO.setSchoolName(operator.getSchoolName());
        return getResult(approveIface.addApprove(approveDO));
    }

    // type 5
    @PostMapping("queryApprove")
    public Proto<List<ApproveDO>> queryApprove(@RequestBody ConditionForApprove condition, HttpServletRequest request) {
        Operator operator = new Operator();
        operator.getRoleGuid();
        // todo 校验role 是否为admin / 教师
        //    EnumForApproveType
        condition.setSchoolId(operator.getSchoolId());
        if (Integer.valueOf(EnumForApproveType.CREATE_ASSOCIATION.getCode()).equals(condition.getApproveType())) {
            return getResult(approveIface.queryApprove(condition));
        }

        return fail("未知类型");
    }

    @PostMapping("queryApproveNew")
    public PaginProto<List<ApproveDO>> queryApproveNew(@RequestBody ConditionForApprove condition, HttpServletRequest request) {
        Operator operator = getOperator(request);
        List<ApproveDO> approveDOS = new ArrayList<>();
        if ("1234".equals(operator.getRoleGuid())) { // 应当由 user 服务提供
            // 判断是否建社申请 or 入社申请
                //判断是否社长
            ConditionForAssociation conditionForAssociation = new ConditionForAssociation();
            conditionForAssociation.setAssociationLeaderGuid(operator.getGuid());
            conditionForAssociation.setPageIndex(1);
            conditionForAssociation.setPageSize(100);
            PaginProto<List<AssociationDO>> paginForAssociation = associationIface.queryAssociation(conditionForAssociation);
            if(paginForAssociation != null && paginForAssociation.getData()!= null){
                List<AssociationDO> res = paginForAssociation.getData();
                if(CollectionUtils.isEmpty(res)){
                    // 非社长
                    condition.setCreateUserGuid(operator.getGuid());
                    PaginProto<List<ApproveDO>> paginProto =  approveIface.queryApprove(condition);
                    return paginProto;
                }else{
                    if(Integer.valueOf(EnumForApproveType.CREATE_ASSOCIATION.getCode()).equals(condition.getApproveType())){
                        // 建社
                        condition.setCreateUserGuid(operator.getGuid());
                        PaginProto<List<ApproveDO>> paginProto =  approveIface.queryApprove(condition);
                        return paginProto;
                    }else if (Integer.valueOf(EnumForApproveType.JOIN_ASSOCIATION.getCode()).equals(condition.getApproveType())){
                        condition.setApproveGuids(res.stream().map(associationDO -> associationDO.getGuid()).collect(Collectors.toList()));
                        return approveIface.queryApprove(condition);
                    }
                }
            }

        }
        if("2234".equals(operator.getRoleGuid())){ // 应当由 user 服务提供
            if(Integer.valueOf(EnumForApproveType.CREATE_ASSOCIATION.getCode()).equals(condition.getApproveType())){
                condition.setSchoolId(condition.getSchoolId());
                return approveIface.queryApprove(condition);
            }
        }

        if ("4234".equals(operator.getRoleGuid())) {
            return approveIface.queryApprove(condition);
        }

        return paginFail();
    }

    @PostMapping("checkLeader")
public Proto<Boolean> checkLeader(HttpServletRequest request){
        Operator operator = getOperator(request);
        ConditionForAssociation condition = new ConditionForAssociation();
        condition.setAssociationLeaderGuid(operator.getGuid());
        condition.setPageIndex(1);
        condition.setPageSize(1);
        Proto<List<AssociationDO>> proto = associationIface.queryAssociation(condition);
        if(proto == null || CollectionUtils.isEmpty(proto.getData())){
            return getResult(Boolean.FALSE);
        }
        return getResult(Boolean.TRUE);
}

@PostMapping("updateApprove")
public Proto<Boolean> updateApprove(@RequestBody ApproveDO approveDO){
//EnumForApproveStatus
    return getResult(approveIface.updateApprove(approveDO));
    }


    @PostMapping("queryAssociation")
    public PaginProto<List<AssociationDO>> queryAssociation(@RequestBody ConditionForAssociation condition , HttpServletRequest request){
        Operator operator = getOperator(request);
        condition.setSchoolId(operator.getSchoolId());
        return associationIface.queryAssociation(condition);
    }
    @PostMapping("queryMyJoinApprove")
    // 1. approving
    // 2. pass
    public Proto<JSONObject> queryMyJoinApprove(HttpServletRequest request){
        JSONObject result = new JSONObject();
        Operator operator = getOperator(request);
        ConditionForApprove condition = new ConditionForApprove();
        condition.setCreateUserGuid(operator.getGuid());
        condition.setApproveStatus(EnumForApproveStatus.START.getCode());
        condition.setApproveType(EnumForApproveType.JOIN_ASSOCIATION.getCode());
        condition.setPageIndex(1);
        condition.setPageSize(1000);
        PaginProto<List<ApproveDO>> paginProto = approveIface.queryApprove(condition);
        if(paginProto != null && paginProto.getData() != null){
            List<ApproveDO> approves = paginProto.getData();
            List<String> approving =approves.stream().map(approveDO -> approveDO.getApproveGuid()).collect(Collectors.toList());
            result.put("approving" , approving);
        }

        ConditionForAssociationUser conditionForAssociationUser = new ConditionForAssociationUser();
        conditionForAssociationUser.setUserGuid(operator.getGuid());
        Proto<List<AssociationUserDO>> proto = associationIface.queryAssociationUser(conditionForAssociationUser);
        if(proto != null && proto.getData() != null){
            List<AssociationUserDO> associationUserDOS = proto.getData();
            List<String> passedAssociationGuids = associationUserDOS.stream().map(asud -> asud.getAssociationGuid()).collect(Collectors.toList());
            result.put("pass",passedAssociationGuids);
        }
        return getResult(result);
    }

    @PostMapping("joinAssociation")
    public Proto<Boolean> joinAssociation(@RequestBody Map<String,String> map , HttpServletRequest request){
        String associationGuid = map.get("associationGuid");
        if(StringUtils.isEmpty(associationGuid)){
            return getResult(false);
        }
        Operator operator = getOperator(request);
        ApproveDO approveDO = new ApproveDO();
        approveDO.setCreateUserGuid(operator.getGuid());
        approveDO.setCreateUserName(operator.getName());
        approveDO.setApproveGuid(associationGuid);
        approveDO.setApproveStatus(EnumForApproveStatus.START.getCode());
        approveDO.setApproveType(EnumForApproveType.JOIN_ASSOCIATION.getCode());
        ApproveDO.DetailForJoinAssociation detail = new ApproveDO.DetailForJoinAssociation();
        detail.setAssociationGuid(approveDO.getApproveGuid());
        ConditionForAssociation conditionForAssociation = new ConditionForAssociation();
        conditionForAssociation.setGuid(associationGuid);
        conditionForAssociation.setPageIndex(1);
        conditionForAssociation.setPageSize(1);
        Proto<List<AssociationDO>> proto = associationIface.queryAssociation(conditionForAssociation);
        if(proto == null && CollectionUtils.isEmpty(proto.getData())){
            return getResult(false);
        }
        detail.setAssociationName(proto.getData().get(0).getName());
        return approveIface.addApprove(approveDO);
    }

    @PostMapping("getJoinAssociation")
    public Proto<List<AssociationDO>> getJoinAssociation(HttpServletRequest request){
        Operator operator =getOperator(request);
        ConditionForAssociationUser condition = new ConditionForAssociationUser();
        condition.setUserGuid(operator.getGuid());
       Proto<List<AssociationUserDO>> protoForAU =  associationIface.queryAssociationUser(condition);
       if(protoForAU != null && !CollectionUtils.isEmpty(protoForAU.getData())){
           List<String> asIds = protoForAU.getData().stream().map(associationUserDO -> associationUserDO.getAssociationGuid()).collect(Collectors.toList());
           ConditionForAssociation conditionForAssociation = new ConditionForAssociation();
           conditionForAssociation.setAssociationGuids(asIds);
           Proto<List<AssociationDO>> proto = associationIface.queryAssociation(conditionForAssociation);
           return proto;
       }
       return getResult(Collections.EMPTY_LIST);
    }
    public static enum RedisKey {
        DEFAULT_CODE,
        PASSWORD_CODE
    }
}
