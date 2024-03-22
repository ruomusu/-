package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
// 服务层，这里将获得的参数进行处理
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 发送验证码功能
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone) == true){
            // 2. 不符合返回错误信息
            return Result.fail("手机格式不正确");
        }
        // 3. 符合生成验证码  hutool github上的开源项目 生成随机六位验证码
        String code = RandomUtil.randomNumbers(6);
/*        // 4. 保存验证码到session
        session.setAttribute("code",code);
*/
        //4. 保存验证码到redis                        login做code验证 login:code
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5. 发送验证码

//        log.debug("发送验证码成功,验证码：{}");
        log.debug("发送验证码成功，验证码为:" + code);
        // 返回ok
        return Result.ok();
    }

    // 登录和注册操作
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、 验证手机号是否正确
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone) == true){
            return Result.fail("手机号错误");
        }
        // 2、 校验验证码
        // 这个code是session保存的code，和用户填的验证码做判断
        Object cachecode = session.getAttribute("code");
        System.out.println("cachecode"+ cachecode);

//        // 2. 通过Redis取出code
//        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        // 不一致就提示错误
        if(cachecode == null || !cachecode.toString().equals(code)){
            return Result.fail("验证码错误");
        }
        // 3、 一致则根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 4、 用户不存在则创建新用户
        if (user == null){
            user = createUser(phone);
            // 4.1 保存用户到数据库
        }
        // 5、 保存用户到session
        // 这里使用hutool的工具类BeanUtil，可以将一个实体类内的属性拷贝到另一个实体，可以选择字节码，或者对象进行拷贝
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        save(user);
        return user;
    }
}
