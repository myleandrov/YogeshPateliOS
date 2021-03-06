package com.medusa.gruul.platform.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medusa.gruul.account.api.conf.MiniInfoProperty;
import com.medusa.gruul.common.core.constant.CommonConstants;
import com.medusa.gruul.common.core.constant.RegexConstants;
import com.medusa.gruul.common.core.constant.TimeConstants;
import com.medusa.gruul.common.core.constant.enums.AuthCodeEnum;
import com.medusa.gruul.common.core.constant.enums.LoginTerminalEnum;
import com.medusa.gruul.common.core.exception.ServiceException;
import com.medusa.gruul.common.core.util.*;
import com.medusa.gruul.common.dto.CurPcUserInfoDto;
import com.medusa.gruul.common.dto.CurUserDto;
import com.medusa.gruul.platform.api.entity.*;
import com.medusa.gruul.platform.conf.MeConstant;
import com.medusa.gruul.platform.conf.PlatformRedis;
import com.medusa.gruul.platform.conf.WechatOpenProperties;
import com.medusa.gruul.platform.constant.RedisConstant;
import com.medusa.gruul.platform.constant.ScanCodeScenesEnum;
import com.medusa.gruul.platform.mapper.AccountInfoMapper;
import com.medusa.gruul.platform.model.dto.*;
import com.medusa.gruul.platform.model.vo.*;
import com.medusa.gruul.platform.service.*;
import lombok.extern.log4j.Log4j2;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.result.WxMpOAuth2AccessToken;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * <p>
 * ??????????????? ???????????????
 * </p>
 *
 * @author whh
 * @since 2019-09-07
 */
@Service
@Log4j2
@Component
@EnableConfigurationProperties(MiniInfoProperty.class)
public class AccountInfoServiceImpl extends ServiceImpl<AccountInfoMapper, AccountInfo> implements IAccountInfoService {

    @Autowired
    private WxMpService wxMpService;
    @Autowired
    private ISendCodeService sendCodeService;

    @Autowired
    private WechatOpenProperties wechatOpenProperties;
    @Autowired
    private IPlatformShopInfoService platformShopInfoService;


    @Override
    public void checkoutAccount(String phone, Integer type) {
        AccountInfo accountInfo = this.baseMapper.selectOne(new QueryWrapper<AccountInfo>().eq("phone", phone));
        //??????????????????
        if (type.equals(CommonConstants.NUMBER_ONE)) {
            if (accountInfo == null) {
                throw new ServiceException("???????????????", SystemCode.DATA_NOT_EXIST.getCode());
            }
            return;
        }
        //?????????????????????
        if (type.equals(CommonConstants.NUMBER_TWO)) {
            if (accountInfo != null) {
                throw new ServiceException("???????????????");
            }
            return;
        }
        throw new ServiceException("????????????");

    }




    /**
     * ??????????????????????????????
     * ??????12?????????
     *
     * @param info ??????????????????
     * @param vo   ????????????????????????
     * @return java.lang.String   redisKey token
     */
    private String cachePlatformCurUserDto(AccountInfo info, AccountInfoVo vo) {
        CurUserDto curUserDto = new CurUserDto();
        curUserDto.setUserId(info.getId().toString());
        curUserDto.setUserType(1);
        curUserDto.setAvatarUrl(info.getAvatarUrl());
        curUserDto.setGender(info.getGender());
        curUserDto.setOpenId(info.getOpenId());
        curUserDto.setNikeName(info.getNikeName());
        LoginShopInfoVo shopInfoVo = vo.getShopInfoVo();
        if (shopInfoVo != null && StrUtil.isNotEmpty(shopInfoVo.getShopId())) {
            curUserDto.setShopId(shopInfoVo.getShopId());
        }
        PlatformRedis platformRedis = new PlatformRedis();
        long between = getTodayEndTime();
        String jwtToken = new JwtUtils(MeConstant.JWT_PRIVATE_KEY).createJwtToken(MeConstant.PLATFORM);
        String redisKey = RedisConstant.TOKEN_KEY.concat(jwtToken);
        platformRedis.setNxPx(redisKey, JSON.toJSONString(curUserDto), between);

        //??????
        CurPcUserInfoDto curPcUserInfoDto = new CurPcUserInfoDto();
        curPcUserInfoDto.setUserId(info.getId().toString());
        curPcUserInfoDto.setTerminalType(LoginTerminalEnum.PC);
        curPcUserInfoDto.setAvatarUrl(info.getAvatarUrl());
        curPcUserInfoDto.setGender(info.getGender());
        curPcUserInfoDto.setOpenId(info.getOpenId());
        curPcUserInfoDto.setNikeName(info.getNikeName());
        PlatformRedis allRedis = new PlatformRedis(CommonConstants.PC_INFO_REDIS_KEY);
        allRedis.setNxPx(jwtToken, JSON.toJSONString(curPcUserInfoDto), between);

        return platformRedis.getBaseKey().concat(":").concat(redisKey);

    }

    /**
     * ???????????????????????????????????????????????????????????????
     *
     * @return 1234ms
     */
    private long getTodayEndTime() {
        Date date = new Date();
        DateTime endOfDay = DateUtil.endOfDay(date);
        return DateUtil.between(date, endOfDay, DateUnit.MS);
    }

    /**
     * ??????????????????????????????
     *
     * @param accountInfoId com.medusa.gruul.platform.api.entity.AccountInfo
     */
    private void updateAccountLastLoignTime(Long accountInfoId) {
        CompletableFuture.runAsync(() -> {
            AccountInfo info = new AccountInfo();
            info.setLastLoginTime(LocalDateTime.now());
            info.setId(accountInfoId);
            this.updateById(info);
        });
    }


    @Override
    public String preAccountScanCode(PreAccountVerifyDto preAccountVerifyDto) {
        if (!ScanCodeScenesEnum.findScenes(preAccountVerifyDto.getScenes())) {
            throw new ServiceException("??????????????????");
        }
        CurUserDto httpCurUser = CurUserUtil.getHttpCurUser();
        if (httpCurUser != null) {
            preAccountVerifyDto.setUserId(Long.valueOf(httpCurUser.getUserId()));
        }
        if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_SHOP_INFO_CHECK.getScenes())) {
            Long shopInfoId = preAccountVerifyDto.getShopInfoId();
            if (shopInfoId == null) {
                throw new ServiceException("shopInfoId????????????");
            }
            PlatformShopInfo platformShopInfo = platformShopInfoService.getById(shopInfoId);
            if (platformShopInfo == null) {
                throw new ServiceException("???????????????");
            }
        }

        String redirectUrl = wechatOpenProperties.getDomain().concat("/account-info/account/verify/notify");
        //???????????????????????????snsapi_login???
        String scope = "snsapi_login";
        String state = SecureUtil.md5(System.currentTimeMillis() + "");
        new PlatformRedis().setNxPx(state, JSONObject.toJSONString(preAccountVerifyDto), TimeConstants.TEN_MINUTES);

        return wxMpService.switchoverTo(preAccountVerifyDto.getAppId()).buildQrConnectUrl(redirectUrl, scope, state);
    }

    @Override
    public void accountScanCodeNotify(String code, String state, HttpServletResponse response) {
        if (StrUtil.isEmpty(state)) {
            throw new ServiceException("????????????");
        }
        PlatformRedis platformRedis = new PlatformRedis();
        String jsonData = platformRedis.get(state);
        if (StrUtil.isEmpty(jsonData)) {
            throw new ServiceException("??????????????????????????????");
        }
        PreAccountVerifyDto preAccountVerifyDto = JSONObject.parseObject(jsonData, PreAccountVerifyDto.class);
        Result result = Result.failed();
        //????????????
        if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_SWITCHING.getScenes())) {
            result = this.changeTie(preAccountVerifyDto.getAppId(), code, preAccountVerifyDto.getUserId());
            //????????????
        } else if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_REGISTER.getScenes())) {
            result = this.createTempAccount(preAccountVerifyDto.getAppId(), code);
        } else if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_LOGGIN.getScenes())) {
            result = this.scanCodeLogin(preAccountVerifyDto.getAppId(), code);
        } else if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_SHOP_INFO_CHECK.getScenes())) {
            result = this.verifyShopAccount(preAccountVerifyDto.getAppId(), code, preAccountVerifyDto.getShopInfoId());
        } else {
            throw new ServiceException("????????????");
        }

        //??????????????????,?????????????????????
        StringBuilder redirectUrl = new StringBuilder(preAccountVerifyDto.getRedirectUrl());
        //?????????????????????????????????
        if (preAccountVerifyDto.getRedirectUrl().contains(MeConstant.WENHAO)) {
            redirectUrl.append("&");
        } else {
            redirectUrl.append("?");
        }
        code = SecureUtil.md5(System.currentTimeMillis() + "");
        redirectUrl.append("code=").append(code);
        //???????????????????????????shopInfoId??????????????????????????????????????????????????????
        if (preAccountVerifyDto.getShopInfoId() != null) {
            redirectUrl.append("&shopInfoId=").append(preAccountVerifyDto.getShopInfoId());
        }
        //????????????????????????????????????5?????????????????????
        platformRedis.setNxPx(code.concat(":inside"), JSONObject.toJSONString(result), TimeConstants.TEN_MINUTES);
        //??????????????????,??????????????????,???????????????
        platformRedis.setNxPx(code, JSONObject.toJSONString(result), TimeConstants.TEN_MINUTES);

        try {
            response.sendRedirect(redirectUrl.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param appId      ????????????appId
     * @param code       ??????code
     * @param shopInfoId ??????id
     * @return com.medusa.gruul.common.core.util.Result
     */
    private Result verifyShopAccount(String appId, String code, Long shopInfoId) {
        try {
            wxMpService.switchoverTo(appId);
            AccountInfo accountInfo = null;
            WxMpOAuth2AccessToken wxMpOauth2AccessToken = wxMpService.oauth2getAccessToken(code);
            if (StrUtil.isNotEmpty(wxMpOauth2AccessToken.getUnionId())) {
                accountInfo = this.getByAccountUnionId(wxMpOauth2AccessToken.getUnionId());
            }
            if (accountInfo == null) {
                accountInfo = this.getByAccountOpenId(wxMpOauth2AccessToken.getOpenId());
                if (accountInfo == null) {
                    return Result.failed("?????????????????????");
                }
            }
            PlatformShopInfo platformShopInfo = platformShopInfoService.getById(shopInfoId);
            if (platformShopInfo == null) {
                return Result.failed("?????????????????????,????????????");
            }
            if (!platformShopInfo.getAccountId().equals(accountInfo.getId())) {
                return Result.failed("?????????????????????????????????????????????");
            }
            return Result.ok(accountInfo);
        } catch (WxErrorException e) {
            e.printStackTrace();
            return Result.failed(e.getError().getErrorMsg());
        }
    }

    /**
     * ??????????????????
     *
     * @param appId ????????????appId
     * @param code  ??????code
     * @return com.medusa.gruul.common.core.util.Result
     */
    private Result scanCodeLogin(String appId, String code) {

        try {
            wxMpService.switchoverTo(appId);
            AccountInfo accountInfo = null;
            WxMpOAuth2AccessToken wxMpOauth2AccessToken = wxMpService.oauth2getAccessToken(code);
            if (StrUtil.isNotEmpty(wxMpOauth2AccessToken.getUnionId())) {
                accountInfo = this.getByAccountUnionId(wxMpOauth2AccessToken.getUnionId());
                if (accountInfo != null) {
                    return Result.ok(accountInfo);
                }
            }
            accountInfo = this.getByAccountOpenId(wxMpOauth2AccessToken.getOpenId());
            //openId??????????????????????????????????????????,??????????????????,?????????????????????
            if (accountInfo == null) {
                accountInfo = new AccountInfo();
                getMpInfo(accountInfo, wxMpOauth2AccessToken, appId);
            }
            return Result.ok(accountInfo);
        } catch (WxErrorException e) {
            e.printStackTrace();
            return Result.failed(e.getError().getErrorMsg());
        }
    }

    /**
     * ????????????????????????,?????????????????????,????????????????????????????????????
     *
     * @param appId ???????????????appid
     * @param code  ???????????????code
     * @return com.medusa.gruul.common.core.util.Result
     */
    private Result<AccountInfo> createTempAccount(String appId, String code) {
        this.wxMpService.switchoverTo(appId);
        try {
            AccountInfo accountInfo = null;
            WxMpOAuth2AccessToken wxMpOauth2AccessToken = wxMpService.oauth2getAccessToken(code);
            if (StrUtil.isNotEmpty(wxMpOauth2AccessToken.getUnionId())) {
                accountInfo = this.getByAccountUnionId(wxMpOauth2AccessToken.getUnionId());
                if (accountInfo != null) {
                    return Result.failed("????????????????????????????????????");
                }
            }
            accountInfo = this.getByAccountOpenId(wxMpOauth2AccessToken.getOpenId());
            if (accountInfo != null) {
                return Result.failed("????????????????????????????????????");
            }
            accountInfo = new AccountInfo();
            getMpInfo(accountInfo, wxMpOauth2AccessToken, appId);
            return Result.ok(accountInfo);
        } catch (WxErrorException e) {
            e.printStackTrace();
            return Result.failed(e.getError().getErrorMsg());
        }

    }

    /**
     * ??????openId ??????????????????
     *
     * @param openId ??????openId
     * @return com.medusa.gruul.platform.api.entity.AccountInfo
     */
    private AccountInfo getByAccountOpenId(String openId) {
        return this.getBaseMapper().selectOne(new QueryWrapper<AccountInfo>().eq("open_id", openId));
    }

    /**
     * ??????unionId ??????????????????
     *
     * @param unionId ????????????unionId
     * @return com.medusa.gruul.platform.api.entity.AccountInfo
     */
    private AccountInfo getByAccountUnionId(String unionId) {
        return this.getBaseMapper().selectOne(new QueryWrapper<AccountInfo>().eq("union_id", unionId));
    }

    @Override
    public LoginAccountInfoDetailVo info() {
        CurPcUserInfoDto curUser = CurUserUtil.getPcRqeustAccountInfo();
        if (curUser == null) {
            throw new ServiceException("????????????", SystemCode.DATA_NOT_EXIST.getCode());
        }
        AccountInfo accountInfo = this.getById(curUser.getUserId());
        AccountInfoVo loginInfoVo = getLoginInfoVo(accountInfo);
        LoginAccountInfoDetailVo vo = new LoginAccountInfoDetailVo();
        BeanUtils.copyProperties(loginInfoVo, vo);
        vo.setBalance(accountInfo.getBalance());
        vo.setAccountType(accountInfo.getAccountType());
        vo.setPhone(accountInfo.getPhone());
        return vo;
    }


    @Override
    public Result<AccountInfo> changeTie(String appId, String code, Long userId) {
        AccountInfo accountInfo = null;
        try {
            WxMpOAuth2AccessToken wxMpOauth2AccessToken = wxMpService.switchoverTo(appId).oauth2getAccessToken(code);
            //?????????????????????????????????
            AccountInfo old = null;
            if (StrUtil.isNotEmpty(wxMpOauth2AccessToken.getUnionId())) {
                old = this.baseMapper.selectOne(new QueryWrapper<AccountInfo>().eq("union_id", wxMpOauth2AccessToken.getUnionId()).notIn("id", userId));
            }
            if (old == null) {
                old = this.baseMapper.selectOne(new QueryWrapper<AccountInfo>().eq("open_id", wxMpOauth2AccessToken.getUnionId()).notIn("id", userId));
            }
            if (old != null) {
                throw new ServiceException("???????????????????????????");
            }
            accountInfo = this.baseMapper.selectById(userId);
            AccountInfo info = getMpInfo(accountInfo, wxMpOauth2AccessToken, appId);

            this.updateById(info);
        } catch (WxErrorException e) {
            e.printStackTrace();
            return Result.failed(e.getMessage());
        } catch (ServiceException e) {
            return Result.failed(e.getMessage());
        }
        return Result.ok(accountInfo);
    }


    @Override
    public void phoneChangeTie(PhoneChangeTieDto phoneChangeTieDto) {
        sendCodeService.certificateCheck(phoneChangeTieDto.getOneCertificate(), phoneChangeTieDto.getOldPhone(), AuthCodeEnum.ACCOUNT_PHONE_IN_TIE.getType());
        AccountInfo phoneAccount = this.getByPhone(phoneChangeTieDto.getNewPhone());
        if (phoneAccount != null) {
            throw new ServiceException("??????????????????????????????");
        }
        sendCodeService.certificateCheck(phoneChangeTieDto.getTwoCertificate(), phoneChangeTieDto.getNewPhone(), AuthCodeEnum.ACCOUNT_PHONE_IN_TIE.getType());
        AccountInfo accountInfo = this.getById(CurUserUtil.getPcRqeustAccountInfo().getUserId());
        if (accountInfo == null) {
            throw new ServiceException("??????token");
        }
        accountInfo.setId(accountInfo.getId());
        accountInfo.setPhone(phoneChangeTieDto.getNewPhone());
        this.baseMapper.updateById(accountInfo);
        removeAccountLogin(accountInfo);

    }

    @Override
    public void passChangeTie(PassChangeTieDto passChangeTieDto) {
        AccountInfo accountInfo = this.getById(CurUserUtil.getPcRqeustAccountInfo().getUserId());
        if (accountInfo == null) {
            throw new ServiceException("????????????");
        }
        if (!accountInfo.getPhone().equals(passChangeTieDto.getPhone())) {
            throw new ServiceException("??????????????????");
        }
        sendCodeService.certificateCheck(passChangeTieDto.getCertificate(), accountInfo.getPhone(), AuthCodeEnum.ACCOUNT_PASSWORD_IN_TIE.getType());
        removeAccountLogin(accountInfo);
        accountInfo.setPassword(passChangeTieDto.getPasswd());
        String salt = RandomUtil.randomString(6);
        accountInfo.setSalt(salt);
        accountInfo.setPasswd(SecureUtil.md5(accountInfo.getPassword().concat(salt)));
        this.baseMapper.updateById(accountInfo);


    }


    @Override
    public AccountInfoVo login(LoginDto loginDto) {
        AccountInfoVo vo = null;
        switch (loginDto.getLoginType()) {
            case 1:
                vo = passwdLogin(loginDto.getPhone(), loginDto.getPassword());
                break;
            case 2:
                vo = phoneCodeLogin(loginDto.getPhone(), loginDto.getCertificate());
                break;
            case 3:
                vo = wxScanCodeLogin(loginDto.getCode());
                break;
            default:
                throw new ServiceException("??????????????????");
        }
        updateAccountLastLoignTime(vo.getId());
        return vo;
    }

    /**
     * @param code code
     * @return
     */
    @Override
    public Result verifyStateResult(String code) {
        PlatformRedis platformRedis = new PlatformRedis();
        String jsonData = platformRedis.get(code);
        if (StrUtil.isEmpty(jsonData)) {
            return Result.failed("code?????????");
        }
        platformRedis.del(code);
        return JSONObject.parseObject(jsonData, Result.class);
    }

    @Override
    public void passwordRetrieve(PasswordRetrieveDto passwordRetrieveDto) {
        AccountInfo accountInfo = getByPhone(passwordRetrieveDto.getPhone());
        if (accountInfo == null) {
            throw new ServiceException("??????????????????");
        }
        removeAccountLogin(accountInfo);
        //????????????????????????????????????
        sendCodeService.certificateCheck(passwordRetrieveDto.getCertificate(), passwordRetrieveDto.getPhone(), AuthCodeEnum.ACCOUNT_FORGET_PASSWD.getType());

        accountInfo.setPassword(passwordRetrieveDto.getPasswd());
        String salt = RandomUtil.randomString(6);
        accountInfo.setSalt(salt);
        accountInfo.setPasswd(SecureUtil.md5(accountInfo.getPassword().concat(salt)));
        this.baseMapper.updateById(accountInfo);


    }

    /**
     * ????????????????????????token
     *
     * @param accountInfo ????????????
     */
    private void removeAccountLogin(AccountInfo accountInfo) {
        PlatformRedis platformRedis = new PlatformRedis();
        String key = SecureUtil.md5(accountInfo.getPhone()).concat(accountInfo.getSalt()).concat(accountInfo.getPasswd());
        String redisKey = RedisConstant.TOKEN_KEY.concat(key);
        platformRedis.del(redisKey);
    }

    /**
     * @param state
     * @return
     */
    private AccountInfoVo wxScanCodeLogin(String state) {
        String jsonData = new PlatformRedis().get(state);
        if (StrUtil.isEmpty(jsonData)) {
            throw new ServiceException("??????????????????");
        }
        Result result = JSONObject.parseObject(jsonData, Result.class);
        if (result.getCode() != CommonConstants.SUCCESS) {
            throw new ServiceException(result.getMsg());
        }
        AccountInfo accountInfo = ((JSONObject) result.getData()).toJavaObject(AccountInfo.class);
        if (accountInfo.getId() == null) {
            throw new ServiceException("????????????????????????");
        }
        return getLoginInfoVo(accountInfo);
    }

    private AccountInfoVo phoneCodeLogin(String phone, String certificate) {
        AccountInfo accountInfo = this.getByPhone(phone);
        if (accountInfo == null) {
            throw new ServiceException("???????????????");
        }
        sendCodeService.certificateCheck(certificate, phone, AuthCodeEnum.MINI_LOGIN.getType());
        return getLoginInfoVo(accountInfo);
    }

    /**
     * ???????????????
     *
     * @param phone    ?????????
     * @param password ??????
     * @return
     */
    private AccountInfoVo passwdLogin(String phone, String password) {
        AccountInfo accountInfo = this.getByPhone(phone);
        if (accountInfo == null) {
            throw new ServiceException("?????????????????????");
        }
        String md5Pw = SecureUtil.md5(password.concat(accountInfo.getSalt()));
        if (!md5Pw.equals(accountInfo.getPasswd())) {
            throw new ServiceException("?????????????????????");
        }
        return getLoginInfoVo(accountInfo);
    }

    /**
     * ??????????????????????????????
     *
     * @param accountInfo ????????????
     * @return com.medusa.gruul.platform.model.vo.AccountInfoVo
     */
    @Override
    public AccountInfoVo getLoginInfoVo(AccountInfo accountInfo) {
        if (accountInfo.getForbidStatus().equals(CommonConstants.NUMBER_ONE)) {
            throw new ServiceException("??????????????????????????????????????????");
        }
        AccountInfoVo vo = new AccountInfoVo();
        BeanUtils.copyProperties(accountInfo, vo);
        //??????????????????
        PlatformShopInfo shopInfo = platformShopInfoService.getOne(null);
        if (shopInfo != null) {
            LoginShopInfoVo infoVo = platformShopInfoService.getLoginShopInfoVo(shopInfo);
            vo.setShopInfoVo(infoVo);
        }
        //??????????????????Token redisKey
        String userToken = cachePlatformCurUserDto(accountInfo, vo);
        vo.setToken(userToken);
        return vo;
    }





    @Override
    public void emailChange(EmailChangeDto emailChangeDto) {
        AccountInfo accountInfo = this.getById(CurUserUtil.getPcRqeustAccountInfo().getUserId());
        if (ObjectUtil.isNull(accountInfo)) {
            throw new ServiceException("????????????");
        }
        AccountInfo up = new AccountInfo();
        up.setId(accountInfo.getId());
        up.setEmail(emailChangeDto.getEmail());
        this.updateById(up);
    }




    @Override
    public Boolean verifyData(VerifyDataDto verifyDataDto) {
        CurUserDto curUser = CurUserUtil.getHttpCurUser();
        if (curUser == null) {
            throw new ServiceException("????????????", SystemCode.DATA_NOT_EXIST.getCode());
        }
        AccountInfo accountInfo = this.getById(curUser.getUserId());
        Boolean flag = Boolean.FALSE;
        if (verifyDataDto.getOption().equals(CommonConstants.NUMBER_ONE)) {
            flag = accountInfo.getPhone().equals(verifyDataDto.getPhone());
        }
        return flag;
    }

    private AccountInfo getMpInfo(AccountInfo accountInfo, WxMpOAuth2AccessToken wxMpOauth2AccessToken, String appId) throws WxErrorException {
        WxMpUser wxMpUser = wxMpService.switchoverTo(appId).oauth2getUserInfo(wxMpOauth2AccessToken, "zh_CN");
        accountInfo.setRefreshToken(wxMpOauth2AccessToken.getRefreshToken());
        accountInfo.setAccessToken(wxMpOauth2AccessToken.getAccessToken());
        accountInfo.setAccessExpiresTime(DateUtils.timestampCoverLocalDateTime(wxMpOauth2AccessToken.getExpiresIn()));
        accountInfo.setOpenId(wxMpOauth2AccessToken.getOpenId());
        accountInfo.setCity(wxMpUser.getCity());
        accountInfo.setLanguage(wxMpUser.getLanguage());
        accountInfo.setNikeName(wxMpUser.getNickname());
        accountInfo.setAvatarUrl(wxMpUser.getHeadImgUrl());
        accountInfo.setGender(wxMpUser.getSex());
        accountInfo.setUnionId(StrUtil.isNotEmpty(wxMpUser.getUnionId()) ? wxMpUser.getUnionId() : null);
        accountInfo.setProvince(wxMpUser.getProvince());
        accountInfo.setCountry(wxMpUser.getCountry());
        accountInfo.setPrivilege(JSON.toJSONString(wxMpUser.getPrivileges()));
        return accountInfo;
    }

    private AccountInfo getByPhone(String username) {
        if (!ReUtil.isMatch(RegexConstants.REGEX_MOBILE_EXACT, username)) {
            throw new ServiceException("???????????????", SystemCode.DATA_NOT_EXIST.getCode());
        }
        return this.baseMapper.selectOne(new QueryWrapper<AccountInfo>().eq("phone", username));
    }
}
