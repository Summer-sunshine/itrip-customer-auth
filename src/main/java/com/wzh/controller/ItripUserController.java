package com.wzh.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.wzh.po.Dto;
import com.wzh.po.ItripUser;
import com.wzh.service.ItripUserSerivce;
import com.wzh.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(value = "/api")
public class ItripUserController {
    @Reference(version = "1.0")
    private ItripUserSerivce itripUserSerivce;

    public ItripUserSerivce getItripUserSerivce() {
        return itripUserSerivce;
    }

    public void setItripUserSerivce(ItripUserSerivce itripUserSerivce) {
        this.itripUserSerivce = itripUserSerivce;
    }

    private Jedis jedis = new Jedis("127.0.0.1", 6379);

    /*手机注册*/
    @RequestMapping(value = "/registerbyphone")
    public Dto codeSave(HttpServletRequest request, HttpServletResponse response, @RequestBody ItripUserVO userVO) {
        System.out.println("手机注册................");
        try {
            System.out.println("获取到的数据：" + userVO.toString());
            //将前台得到的数据封装到po
            ItripUser user = new ItripUser();
            user.setUsercode(userVO.getUserCode());//手机号码
            user.setUserpassword(userVO.getUserPassword());//密码
            user.setUsertype(0);//区分第三方注册
            user.setUsername(userVO.getUserName());//用户名、
            user.setActivated(0);//是否激活
            //判断是否已注册
            ItripUser olduser = itripUserSerivce.findByUserCode(user);
            if (olduser == null) {
                //没有注册
                //处理密码MD5加密
                user.setUserpassword(MD5Util.getMd5(user.getUserpassword(), 32));
                System.out.println("密码加密后：" + user.toString());
                //添加用户
                itripUserSerivce.codeUserSave(user);
                //发送短信验证码
                String codecheck = SMSUtil.testcheck(user.getUsercode());
                //redis缓存验证码
                jedis.setex(user.getUsercode(), 120, codecheck);
                return DtoUtil.returnSuccess();
            } else {
                //已经注册（是否激活）
                if (olduser.getActivated() == 0 && jedis.get(olduser.getUsercode()) == null) {
                    //用户注册过，但没有激活账号
                    //发送短信验证码
                    String codecheck = SMSUtil.testcheck(user.getUsercode());
                    //redis缓存验证码
                    jedis.setex(user.getUsercode(), 120, codecheck);
                    return DtoUtil.returnFail("用户已存在，但未激活，请重新激活", ErrorCode.AUTH_AUTHENTICATION_FAILED);
                }
                System.out.println("用户已存在");
                return DtoUtil.returnFail("用户已存在，注册失败", ErrorCode.AUTH_AUTHENTICATION_UPDATE);
            }
        } catch (Exception e) {
            return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
        }
    }

    /*手机验证
     * user --  手机号
     * code --  验证码
     * */
    @RequestMapping(value = "/validatephone")
    public Dto validatePhone(HttpServletRequest request, HttpServletResponse response, String user, String code) {
        System.out.println("手机验证。。。");
        try {
            //获取redis中改手机号的缓存数据
            System.out.println(jedis.get(user));
            if (jedis.get(user) != null) {
                //匹配
                if (jedis.get(user).equals(code)) {
                    ItripUser users = new ItripUser();
                    users.setUsercode(user);
                    //修改状态
                    itripUserSerivce.upadteUserActivated(users);
                    return DtoUtil.returnSuccess("激活成功");
                }
            } else {
                return DtoUtil.returnFail("验证失败", ErrorCode.AUTH_AUTHENTICATION_FAILED);
            }
        } catch (Exception e) {
            return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
        }
        return null;
    }

    /*用户验证*/
    @RequestMapping(value = "/ckusr")
    public @ResponseBody Dto checkUser(String name) {
        System.out.println("用户重复验证...........");
        try {
            ItripUser user = new ItripUser();
            user.setUsercode(name);
            if (null == itripUserSerivce.findByUserCode(user)) {
                return DtoUtil.returnSuccess("用户名可用");
            } else {
                System.out.println("用户已存在");
                return DtoUtil.returnFail("用户已存在，注册失败", ErrorCode.AUTH_AUTHENTICATION_UPDATE);
            }
        } catch (Exception e) {
            return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
        }
    }

    /*邮箱注册*/
    @RequestMapping(value = "/doregister")
    public Dto doRegister(@RequestBody ItripUserVO userVO) {
        System.out.println("邮箱注册..............");
        try {
            //将前台数据封装到po对象
            ItripUser user = new ItripUser();
            user.setUsercode(userVO.getUserCode());//手机号码
            user.setUserpassword(userVO.getUserPassword());//密码
            user.setUsertype(0);//区分第三方注册
            user.setUsername(userVO.getUserName());//用户名、
            user.setActivated(0);//是否激活
            //判断是否已注册
            ItripUser olduser = itripUserSerivce.findByUserCode(user);
            if (olduser == null) {
                //没有注册
                //处理密码MD5加密
                user.setUserpassword(MD5Util.getMd5(user.getUserpassword(), 32));
                System.out.println("密码加密后：" + user.toString());
                //添加用户
                itripUserSerivce.codeUserSave(user);
                //发送邮箱验证码
                String emailcheck = EmailUtil.emailregister(user);
                //缓存验证码
                jedis.setex(user.getUsercode(), 120, emailcheck);
                return DtoUtil.returnSuccess();
            } else {
                //已经注册（是否激活）
                if (olduser.getActivated() == 0 && jedis.get(olduser.getUsercode()) == null) {
                    //用户注册过，但没有激活账号
                    //发送邮箱验证码
                    String emailcheck = EmailUtil.emailregister(user);
                    //缓存验证码
                    jedis.setex(user.getUsercode(), 120, emailcheck);
                    return DtoUtil.returnFail("用户已存在，但未激活，请重新激活", ErrorCode.AUTH_AUTHENTICATION_FAILED);
                }
                System.out.println("用户已存在");
                return DtoUtil.returnFail("用户已存在，注册失败", ErrorCode.AUTH_AUTHENTICATION_UPDATE);
            }
        } catch (Exception e) {
            return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
        }
    }
    /*邮箱验证(user -- 手机号 code -- 验证码)*/
    @RequestMapping(value = "/activate")
    public Dto activate(HttpServletRequest request, HttpServletResponse response, String user, String code) {
        System.out.println("邮箱验证.........");
        try {
            //获取redis中改手机号的缓存数据
            System.out.println(jedis.get(user));
            if (jedis.get(user) != null) {
                //匹配
                if (jedis.get(user).equals(code)) {
                    ItripUser users = new ItripUser();
                    users.setUsercode(user);
                    //修改状态
                    itripUserSerivce.upadteUserActivated(users);
                    return DtoUtil.returnSuccess("激活成功");
                }
            }else {
                ItripUser users = new ItripUser();
                users.setUsercode(user);
                //判断是否已注册
                ItripUser olduser = itripUserSerivce.findByUserCode(users);
                //已经注册（是否激活）
                if (olduser.getActivated() == 0 && jedis.get(olduser.getUsercode()) == null) {
                    //用户注册过，但没有激活账号
                    //发送邮箱验证码
                    String emailcheck = EmailUtil.emailregister(users);
                    //缓存验证码
                    jedis.setex(users.getUsercode(), 120, emailcheck);
                    return DtoUtil.returnFail("用户已存在，但未激活，请重新激活", ErrorCode.AUTH_AUTHENTICATION_FAILED);
                }
                return DtoUtil.returnFail("验证失败", ErrorCode.AUTH_AUTHENTICATION_FAILED);
            }
        } catch (Exception e) {
            return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
        }
        return null;
    }
}
