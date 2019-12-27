package com.wzh.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.wzh.po.Dto;
import com.wzh.po.ItripUser;
import com.wzh.service.ItripUserSerivce;
import com.wzh.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;

@RestController
@RequestMapping(value = "/api")
public class ItripLoginController {
    @Reference(version = "1.0")
    private ItripUserSerivce itripUserSerivce;

    public ItripUserSerivce getItripUserSerivce() {
        return itripUserSerivce;
    }

    public void setItripUserSerivce(ItripUserSerivce itripUserSerivce) {
        this.itripUserSerivce = itripUserSerivce;
    }
    private static Jedis jedis=new Jedis("127.0.0.1",6379);
    /*用户登录*/
    @RequestMapping(value="/dologin")
    public Dto dologin(HttpServletRequest request, HttpServletResponse response,String name,String password){
        System.out.println("用户登录.........");
        ItripUser user=null;
        user=itripUserSerivce.doLogin(new ItripUser(name.trim(), MD5Util.getMd5(password.trim(),32)));
        System.out.println(user.toString());
        if (user!=null){
            //登陆成功
            //生成token
            String token= TokenUtil.getTokenGenerator(request.getHeader("user-agent"),user);
            //缓存token
            if (token.startsWith("token:PC-")){
                //将token作为key，user作为value,存入redis（因为将来取值需要返回对象，所以value转成json）
                String strJSON= JSON.toJSONString(user);
                jedis.setex(token,7200,strJSON);
                //专门存token
                jedis.setex("token",7200,token);
            }
            ItripTokenVO tokenVO=new ItripTokenVO(token, Calendar.getInstance().getTimeInMillis()+60*60*1000*2,Calendar.getInstance().getTimeInMillis());
            System.out.println("登录成功返回参数："+tokenVO.toString());
            return DtoUtil.returnDataSuccess(tokenVO);
        }else {
            System.out.println("账号密码错误，登录失败");
            return DtoUtil.returnFail("用户名密码错误", ErrorCode.AUTH_AUTHENTICATION_FAILED);
        }
    }
    /*退出登录*/
    @RequestMapping(value="/logout")
    public Dto logout(HttpServletRequest request){
        System.out.println("账号注销.....................");
        String token=jedis.get("token");
        if (!TokenUtil.validate(request.getHeader("user-agent"),token)){
            return DtoUtil.returnFail("token无效",ErrorCode.AUTH_TOKEN_INVALID);
        }
        try {
            //删除token信息
            TokenUtil.delete(token);
            return DtoUtil.returnSuccess("注销成功");
        } catch (Exception e) {
            e.printStackTrace();
            return DtoUtil.returnSuccess("注销失败",ErrorCode.AUTH_TOKEN_INVALID);
        }
    }
    public static void dalete(String token){
        if (jedis.get(token)!=null){
            jedis.del(token);
        }
    }
    //token置换
    @RequestMapping(value="/retoken")
    public @ResponseBody Dto replace(HttpServletRequest request){
        System.out.println("置换token.........");
        String agent=request.getHeader("user-agent");
        String token=jedis.get("token");
        if (token==null){
            return DtoUtil.returnFail("未知的token或token已过期，请重新登录",ErrorCode.AUTH_UNKNOWN);
        }
        try {
        String newtoken=TokenUtil.replaceToken(agent,token);
        return DtoUtil.returnSuccess("token置换成功",newtoken);
        } catch (TokenValidationFailedException e) {
            e.printStackTrace();
            return DtoUtil.returnFail("token置换失败",ErrorCode.AUTH_UNKNOWN);
        }
    }
}
