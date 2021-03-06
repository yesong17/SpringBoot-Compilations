package com.wjwcloud.iot.voicecontrol.aligenie.controller;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wjwcloud.iot.customer.commons.constant.CustomerConstant;
import com.wjwcloud.iot.model.ResultResponse;
import com.wjwcloud.iot.product.entity.ProductDevice;
import com.wjwcloud.iot.product.service.ProductDeviceService;
import com.wjwcloud.iot.utils.key.UUIDUtil;
import com.wjwcloud.iot.utils.redis.RedisProxy;
import com.wjwcloud.iot.voicecontrol.aligenie.common.AligenieConstantKey;
import com.wjwcloud.iot.voicecontrol.aligenie.test.controller.AuthzController;
import com.wjwcloud.iot.voicecontrol.aligenie.service.IAligenieAuthService;
import com.wjwcloud.iot.voicecontrol.aligenie.utils.AligenieUtil;
import com.wjwcloud.iot.voicecontrol.common.ConstantKey;
import com.wjwcloud.iot.voicecontrol.service.OauthCallBackService;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.oltu.oauth2.as.issuer.MD5Generator;
import org.apache.oltu.oauth2.as.issuer.OAuthIssuer;
import org.apache.oltu.oauth2.as.issuer.OAuthIssuerImpl;
import org.apache.oltu.oauth2.as.request.OAuthAuthzRequest;
import org.apache.oltu.oauth2.as.request.OAuthTokenRequest;
import org.apache.oltu.oauth2.as.response.OAuthASResponse;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthBearerClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthResourceResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.error.OAuthError;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.OAuthResponse;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authorization Code 授权码模式
 * Created by zhoulei on 2019/4/25.
 * Impl OAth2  http://oauth.net/2/
 */
@Controller
@RequestMapping("/aligenie")
public class AligenieController {

    private static Logger logger = LoggerFactory.getLogger(AuthzController.class);

    private Cache cache;

    @Autowired
    public AligenieController(CacheManager cacheManager) {
        this.cache = cacheManager.getCache("oauth2-cache");
    }

    /**
     * 注入天猫精灵登录服务
     */
    @Resource(name = "aligenieLoginServiceImpl")
    private IAligenieAuthService iAligenieAuthService;

    @Resource (name = "oauthCallBackServiceImpl")
    private OauthCallBackService oauthCallBackService;

    @Autowired
    private ProductDeviceService productDeviceService;

    /**
     * 注入redis服务
     */
    @Autowired
    private RedisProxy redisProxy;

    /* *
     * 构建OAuth2授权请求 [需要client_id与redirect_uri绝对地址]
     * @param request
     * @param session
     * @param model
     * @return 返回授权码(code)有效期10分钟，客户端只能使用一次[与client_id和redirect_uri一一对应关系]
     * @throws OAuthSystemException
     * @throws IOException
     * @url  http://wechat.tunnel.geer2.com:8047/oauth2/authorize?client_id={AppKey}&response_type=code&redirect_uri={YourSiteUrl}
     * @test http://wechat.tunnel.geer2.com:8047/oauth2/authorize?client_id=fbed1d1b4b1449daa4bc49397cbe2350&response_type=code&redirect_uri=http://wechat.tunnel.geer2.com:8047/client/oauthCallback
     */

//    /**
//     * 用户登录
//     * @param request
//     * @param session
//     * @param params
//     * @return
//     */
//    @RequestMapping(value = "/login")
//    @ResponseBody
//    public ResultResponse authorize(HttpServletRequest request, HttpSession session, @RequestBody Map<String, Object> params) {
//        try {
//            if(!params.containsKey("mobilePhone")){
//                return ResultResponse.FAILED("请输入手机号");
//            }
//            if(!params.containsKey("loginType")){
//                return ResultResponse.FAILED("请传入正确的参数");
//            }
//
//            Map token = iAligenieAuthService.login(params);
//            if (null != token) {
//                return ResultResponse.SUCCESSFUL(token);
//            } else {
//                return ResultResponse.FAILED("登录失败");
//            }
//        } catch (Exception ex) {
//            return ResultResponse.FAILED(ex.getMessage());
//        }
//    }
//
//
//    //天猫精灵调用登录服务
//    @RequestMapping(value = "/aligenielogin" ,method = RequestMethod.GET)
//    public void login(HttpServletRequest request , HttpServletResponse response, BufferedReader br) throws Exception{
//        iAligenieAuthService.aligenielogin(request,response,br);
//    }
//
//    /**
//     * 登录成功后回调服务
//     * @param
//     * @param
//     * @param params
//     * @return
//     */
//    @RequestMapping(value = "/oauthCallback")
//    @ResponseBody
//    public ResultResponse oauthCallback( @RequestBody Map<String, Object> params) {
//        logger.info("登录、授权成功后回调、获得令牌并返回");
//        //发布访问令牌，解码url并且将code加在url后面返回
//        String redirect_uri = oauthCallBackService.oauthCallback(params);
//        return ResultResponse.SUCCESSFUL(redirect_uri);
//    }
//
//    /**
//     * 认证服务器申请令牌(AccessToken) [验证client_id、client_secret、auth code的正确性或更新令牌 refresh_token]
//     * @param request
//     * @param response
//     * @return
//     * @url http://wechat.tunnel.geer2.com:8047/oauth2/accessToken?client_id={AppKey}&client_secret={AppSecret}&grant_type=authorization_code&redirect_uri={YourSiteUrl}&code={code}
//     */
//    @RequestMapping(value = "/accessToken",method = RequestMethod.POST)
//    public void accessToken(HttpServletRequest request, HttpServletResponse response)
//            throws IOException, OAuthSystemException {
//        logger.info("智能音箱授权成功回调");
////        UrlUtil.getAllRequestParam(request);
////        UrlUtil.getAllHeadParam(request);
////        String requestURI = request.getRequestURL().toString();
//        PrintWriter out = null;
//        OAuthIssuer oauthIssuerImpl = new OAuthIssuerImpl(new MD5Generator());
//        try {
//            out = response.getWriter();
//            HttpSession session = request.getSession();
//            String client_id = (String) session.getAttribute("client_id");
//            String resultData = AligenieUtil.getJsonData(AligenieUtil.requestGetParams(request , "code"));
//            logger.info("返回天猫精灵token：" + resultData);
//            response.setContentType("application/json");
//            out.write(resultData);
//            out.flush();
//            out.close();
//            return;
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//
//    /**
//     * 刷新令牌
//     * @param request
//     * @param response
//     * @throws IOException
//     * @throws OAuthSystemException
//     * @url http://wechat.tunnel.geer2.com:8047/oauth2/refresh_token?client_id={AppKey}&grant_type=refresh_token&refresh_token={refresh_token}
//     */
//    @RequestMapping(value = "/refreshToken",method = RequestMethod.POST)
//    public void refresh_token(HttpServletRequest request, HttpServletResponse response)
//            throws IOException, OAuthSystemException {
//        PrintWriter out = null;
//    }


    @RequestMapping("/client")
    public String  loginPage(HttpServletRequest request,Model model){
        try {
            System.out.println("执行到这里");
//            OAuthClientRequest oauthResponse = OAuthClientRequest
////                    .authorizationLocation(AligenieConstantKey.OAUTH_CLIENT_AUTHORIZE)
////                    .setResponseType(OAuth.OAUTH_CODE)
////                    .setClientId(AligenieConstantKey.OAUTH_CLIENT_ID)
////                    .setRedirectURI(AligenieConstantKey.OAUTH_CLIENT_CALLBACK)
////                    .setScope(AligenieConstantKey.OAUTH_CLIENT_SCOPE)
////                    .setState("111")
////                    .buildQueryMessage();
            Map<String,String> map = AligenieUtil.getAllRequestParam(request);
            String reUri = map.get("redirect_uri");
            String clientId = map.get("client_id");
            String reType = map.get("response_type");
            String state = map.get("state");
            System.out.println("flow login Oauth=="+reUri);
            OAuthClientRequest oauthResponse = OAuthClientRequest
                    .authorizationLocation(AligenieConstantKey.OAUTH_CLIENT_AUTHORIZE)
                    .setResponseType(OAuth.OAUTH_CODE)
                    .setClientId(AligenieConstantKey.OAUTH_CLIENT_ID)
                    .setRedirectURI(reUri)
                    .setScope(AligenieConstantKey.OAUTH_CLIENT_SCOPE)
                    .setState(state)
                    .buildQueryMessage();
            System.out.println("flow==client:"+oauthResponse.getLocationUri());
            return "redirect:" + oauthResponse.getLocationUri();
//            return "redirect:"+AligenieConstantKey.AUTH_LOCATION_OAUTH_CLIENT_AUTHORIZE + "?redirect_uri=http%3A%2F%2Fgzue.natapp1.cc%2Faligenie%2FoauthCallback"  + "&state=" + 111 + "&client_id=" + AligenieConstantKey.OAUTH_CLIENT_ID
//                    + "&response_type=code" + "&grant_type=authorization_code";
        } catch (OAuthSystemException e) {
            e.printStackTrace();
        }
//        String clientName= "App Name";
//        model.addAttribute("clientName",clientName);
//        model.addAttribute("response_type",OAuth.OAUTH_CODE);
//        model.addAttribute("client_id",AligenieConstantKey.OAUTH_CLIENT_ID);
//        model.addAttribute("redirect_uri",AligenieConstantKey.OAUTH_CLIENT_CALLBACK);
//        model.addAttribute("scope",AligenieConstantKey.OAUTH_CLIENT_SCOPE);
        return "index";
    }

    @RequestMapping(value = "/authorize")
    public String authorize(HttpServletRequest request, HttpSession session, Model model)
            throws OAuthSystemException, IOException {
        System.out.println("flow==auth start");
        try {

            //构建OAuth请求
            OAuthAuthzRequest oauthRequest = new OAuthAuthzRequest(request);

            //查询客户端Appkey应用的信息
            String clientName= "App Name";
            model.addAttribute("clientName",clientName);
            model.addAttribute("response_type",oauthRequest.getResponseType());
            model.addAttribute("client_id",oauthRequest.getClientId());
            model.addAttribute("redirect_uri",oauthRequest.getRedirectURI());
            model.addAttribute("state",oauthRequest.getState());
            model.addAttribute("scope",oauthRequest.getScopes());
            //验证用户是否已登录
            if(session.getAttribute(AligenieConstantKey.MEMBER_SESSION_KEY)==null) {
                //用户登录
                if("1".equals(validateOAuth2Pwd(request).getCode())) {
                    //登录失败跳转到登陆页
                    System.out.println("flow==oauth2/login");
                    return "index";
                }
            }
            //判断此次请求是否是用户授权
            if(request.getParameter("action")==null||!request.getParameter("action").equalsIgnoreCase("authorize")){
                //到申请用户同意授权页
                System.out.println("flow==oauth2/authorize");
                return "authorize";
            }
            //生成授权码 UUIDValueGenerator OR MD5Generator
            String authorizationCode = new OAuthIssuerImpl(new MD5Generator()).authorizationCode();
            //把授权码存入缓存
            cache.put(authorizationCode, DigestUtils.sha1Hex(oauthRequest.getClientId()+oauthRequest.getRedirectURI()));
            //构建oauth2授权返回信息
            OAuthResponse oauthResponse = OAuthASResponse
                    .authorizationResponse(request,HttpServletResponse.SC_FOUND)
                    .setCode(authorizationCode)
                    .location(oauthRequest.getParam(OAuth.OAUTH_REDIRECT_URI))
                    .buildQueryMessage();
            //申请令牌成功重定向到客户端页
            System.out.println("flow==auth:"+oauthResponse.getLocationUri());
            return "redirect:"+oauthResponse.getLocationUri();
        } catch(OAuthProblemException ex) {
            OAuthResponse oauthResponse = OAuthResponse
                    .errorResponse(HttpServletResponse.SC_UNAUTHORIZED)
                    .error(ex)
                    .buildJSONMessage();
            logger.error("oauthRequest.getRedirectURI() : " + ex.getRedirectUri() + " oauthResponse.getBody() : " + oauthResponse.getBody());
            model.addAttribute("errorMsg", oauthResponse.getBody());
            return  "/oauth2/error";
        }
    }

    @RequestMapping(value = "/oauthCallback" ,method = RequestMethod.GET)
    public String getToken(HttpServletRequest request,Model model) throws OAuthProblemException {
        System.out.println("flow==client callback start");
        OAuthAuthzResponse oauthAuthzResponse = null;
        try {
            oauthAuthzResponse = OAuthAuthzResponse.oauthCodeAuthzResponse(request);
            String code = oauthAuthzResponse.getCode();
            OAuthClientRequest oauthClientRequest = OAuthClientRequest
                    .tokenLocation(AligenieConstantKey.OAUTH_CLIENT_ACCESS_TOKEN)
                    .setGrantType(GrantType.AUTHORIZATION_CODE)
                    .setClientId(ConstantKey.OAUTH_CLIENT_ID)
                    .setClientSecret(ConstantKey.OAUTH_CLIENT_SECRET)
                    .setRedirectURI(AligenieConstantKey.OAUTH_CLIENT_CALLBACK)
                    .setCode(code)
                    .buildQueryMessage();
            OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());

            //Facebook is not fully compatible with OAuth 2.0 draft 10, access token response is
            //application/x-www-form-urlencoded, not json encoded so we use dedicated response class for that
            //Custom response classes are an easy way to deal with oauth providers that introduce modifications to
            //OAuth 2.0 specification

            //获取access token
            OAuthJSONAccessTokenResponse oAuthResponse = oAuthClient.accessToken(oauthClientRequest, OAuth.HttpMethod.POST);
            String accessToken = oAuthResponse.getAccessToken();
            String refreshToken= oAuthResponse.getRefreshToken();
            Long expiresIn = oAuthResponse.getExpiresIn();
            //获得资源服务
            OAuthClientRequest bearerClientRequest = new OAuthBearerClientRequest(AligenieConstantKey.OAUTH_GET_SOURCE)
                    .setAccessToken(accessToken).buildQueryMessage();
            OAuthResourceResponse resourceResponse = oAuthClient.resource(bearerClientRequest, OAuth.HttpMethod.GET, OAuthResourceResponse.class);
            String resBody = resourceResponse.getBody();

            logger.info("accessToken: "+accessToken +" refreshToken: "+refreshToken +" expiresIn: "+expiresIn +" resBody: "+resBody);
            model.addAttribute("accessToken",  "accessToken: "+accessToken +" resBody: "+resBody);
            return "token";
        } catch (OAuthSystemException ex) {
            logger.error("getToken OAuthSystemException : " + ex.getMessage());
            model.addAttribute("errorMsg",  ex.getMessage());
            return  "/oauth2/error";
        }
    }

    @RequestMapping(value = "/accessToken",method = RequestMethod.POST)
    public void accessToken(HttpServletRequest request, HttpServletResponse response)
            throws IOException, OAuthSystemException {
        logger.info("智能音箱授权成功回调");
       System.out.println("flow==access token start");
            PrintWriter out = null;
            OAuthIssuer oauthIssuerImpl = new OAuthIssuerImpl(new MD5Generator());
        try {
            out = response.getWriter();
            //构建oauth2请求
            OAuthTokenRequest oauthRequest = new OAuthTokenRequest(request);

            String authzCode = oauthRequest.getCode();
            //验证AUTHORIZATION_CODE , 其他的还有PASSWORD 或 REFRESH_TOKEN (考虑到更新令牌的问题，在做修改)
            if (GrantType.AUTHORIZATION_CODE.name().equalsIgnoreCase(oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE))) {
                if (cache.get(authzCode) == null) {
                    OAuthResponse oauthResponse = OAuthASResponse
                                                  .errorResponse(HttpServletResponse.SC_UNAUTHORIZED)
                                                  .setError(OAuthError.TokenResponse.INVALID_GRANT)
                                                  .setErrorDescription(AligenieConstantKey.INVALID_CLIENT_GRANT)
                                                  .buildJSONMessage();
                    out.write(oauthResponse.getBody());
                    out.flush();
                    out.close();
                    return;
                }
            }
            //生成token
            final String accessToken = oauthIssuerImpl.accessToken();
            String refreshToken = oauthIssuerImpl.refreshToken();
            //cache.put(accessToken,cache.get(authzCode).get());
            cache.put(refreshToken,accessToken);
            logger.info("accessToken : "+accessToken +"  refreshToken: "+refreshToken);
            //清除授权码 确保一个code只能使用一次
            cache.evict(authzCode);
            //构建oauth2授权返回信息
            OAuthResponse oauthResponse = OAuthASResponse
                                          .tokenResponse(HttpServletResponse.SC_OK)
                                          .setAccessToken(accessToken)
                                          .setExpiresIn("3600")
                                          .setRefreshToken(refreshToken)
                                          .buildJSONMessage();
            response.setStatus(oauthResponse.getResponseStatus());
            response.setContentType("application/json");
            out.print(oauthResponse.getBody());
            out.flush();
            out.close();
        } catch(OAuthProblemException ex) {
            OAuthResponse oauthResponse = OAuthResponse
                                          .errorResponse(HttpServletResponse.SC_UNAUTHORIZED)
                                          .error(ex)
                                          .buildJSONMessage();
            response.setStatus(oauthResponse.getResponseStatus());
            out.print(oauthResponse.getBody());
            out.flush();
            out.close();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
        finally
        {
            if (null != out){ out.close();}
        }
    }

//    @RequestMapping("/getResource")
////    @ResponseBody
//    public void get_resource(HttpServletRequest request, HttpServletResponse response)
//            throws IOException, OAuthSystemException{
//        PrintWriter out = null;
//        try {
//            System.out.println("flow==getResource Start");
//            out = response.getWriter();
//            //构建oauth2资源请求
//            OAuthAccessResourceRequest oauthRequest = new OAuthAccessResourceRequest(request, ParameterStyle.QUERY);
//            //获取验证accesstoken
//            String accessToken = oauthRequest.getAccessToken();
//            System.out.println("flow==accessToken="+oauthRequest.getAccessToken());
//            //验证accesstoken是否存在或过期
////            if (accessToken.isEmpty()||cache.get(accessToken)== null) {
////                OAuthResponse oauthResponse = OAuthRSResponse
////                        .errorResponse(HttpServletResponse.SC_UNAUTHORIZED)
////                        .setRealm("RESOURCE_SERVER_NAME")
////                        .setError(OAuthError.ResourceResponse.INVALID_TOKEN)
////                        .setErrorDescription(OAuthError.ResourceResponse.EXPIRED_TOKEN)
////                        .buildHeaderMessage();
////                response.addDateHeader(OAuth.HeaderType.WWW_AUTHENTICATE, Long.parseLong(oauthResponse.getHeader(OAuth.HeaderType.WWW_AUTHENTICATE)));
////            }
//            //获得用户名
//            String mobilephone = "16620069844"; //oAuthService.getNameByAccessToken(accessToken);
//            out.print(mobilephone);
//            out.flush();
//            out.close();
//        } catch (OAuthProblemException ex) {
//            logger.error("ResourceController OAuthProblemException : "+ex.getMessage());
//            OAuthResponse oauthResponse = OAuthRSResponse
//                    .errorResponse(HttpServletResponse.SC_UNAUTHORIZED)
//                    .setRealm("get_resource exception")
//                    .buildHeaderMessage();
//            response.addDateHeader(OAuth.HeaderType.WWW_AUTHENTICATE, Long.parseLong(oauthResponse.getHeader(OAuth.HeaderType.WWW_AUTHENTICATE)));
//        }
//        finally {
//            if (null != out){ out.close();}
//        }
//    }

    private ResultResponse validateOAuth2Pwd(HttpServletRequest request){

            String mobilePhone = request.getParameter("mobilePhone");
            String password = request.getParameter("password");
            if(StringUtils.isBlank(mobilePhone)){
                return ResultResponse.FAILED();
            }
//            if(!params.containsKey("loginType")){
//                return false;
//            }
            Map<String,Object> params = new HashMap<>();
            params.put("mobilePhone",mobilePhone);
            params.put("password",password);
            params.put("loginType",CustomerConstant.LOGIN_TYPE_PASSWORD);
            Map token = iAligenieAuthService.login(params);
            if (null != token) {
                request.getSession().setAttribute(AligenieConstantKey.MEMBER_SESSION_KEY,mobilePhone);
                return ResultResponse.SUCCESSFUL(token);
            } else {
                return ResultResponse.FAILED();
            }
    }


//    @RequestMapping("/getResource")
    @ResponseBody
    public JSONObject get_resource(HttpServletRequest request, HttpServletResponse response , BufferedReader br)
            throws IOException, OAuthSystemException{
        System.out.println("访问地址： " + request.getRequestURI());
        //要返回的设备列表
        JSONObject merchineList = new JSONObject();
          String bodyStrin  = AligenieUtil.getAllBodyParam(br);
        AligenieUtil.getAllHeadParam(request);
        AligenieUtil.getAllRequestParam(request);
        String token = getClaimsByToken(bodyStrin);
          //获取设备列表
          if(!"".equals(bodyStrin)){
              //根据天猫精灵调用传过来的参数获取token并解析出用户信息
//              Claims claims = getClaimsByToken(bodyStrin);

              long userId = 0L ;
//              if(null != claims){
                  try {
                      //获取用户ID
                      userId = 1;
                      Map map = new HashMap();
//                      map.put("customerId" , userId);
//                      map.put("type" , ProductTypeEnum.FAN.getCode());
//                      map.put("isDeleted" , 0);
                      //获取该用户下所有设备列表
                      List<ProductDevice> productDeviceList = productDeviceService.findListOfCustomer(map);
                      System.out.println("该用户下设备列表为：" + productDeviceList.size());
                      if(productDeviceList.size() > 0 ){
                          merchineList = getMerchineList(bodyStrin , productDeviceList);
//                        merchineList = getMercherListTest(bodyStrin );
                      }
                  } catch (NumberFormatException e) {
                      e.printStackTrace();
                  } catch (Exception e) {
                      e.printStackTrace();
                  }
//              }
              return merchineList;
          }
          //操作设备
          else{
              System.out.println("设备操作");
              return getMercherListTest(token);
          }


    }

    /**
     * 天猫精灵回调设备控制服务
     * @param request
     * @param response
     * @param br
     * @return
     * @throws IOException
     * @throws OAuthSystemException
     */
//    @RequestMapping("/DeviceControl")
//    @ResponseBody
//    public JSONObject DeviceControl(HttpServletRequest request, HttpServletResponse response , BufferedReader br)
//            throws IOException, OAuthSystemException{
//        //要返回的设备列表
//        JSONObject merchineList = new JSONObject();
////        String bodyStrin  = AligenieUtil.getAllBodyParam(br);
////        //根据天猫精灵调用传过来的参数获取token并解析出用户信息
////        Claims claims = getClaimsByToken(bodyStrin);
//        long userId = 0L ;
////        if(null != claims){
//            try {
//                //获取用户ID
//                userId = 1;
//                Map map = new HashMap();
//                map.put("customerId" , userId);
//                //获取该用户下所有设备列表
//                List<ProductDevice> productDeviceList = productDeviceService.findListOfCustomer(map);
//                System.out.println("该用户下设备列表为：" + productDeviceList.size());
//                if(productDeviceList.size() > 0 ){
//                    merchineList = getMerchineList(bodyStrin , productDeviceList);
////                        merchineList = getMercherListTest(bodyStrin );
//                }
//            } catch (NumberFormatException e) {
//                e.printStackTrace();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
////        }
//
//        return merchineList;
//    }


    /**
     根据天猫精灵调用传过来的参数获取token并解析出用户信息
     */
    private String  getClaimsByToken (String bodyStrin){
        JSONObject recieveHeader = new JSONObject();
        //body部分
        recieveHeader = JSON.parseObject(bodyStrin);
        //获取head属性
        String str1 = recieveHeader.getString("header");
        JSONObject payload = new JSONObject();
        //获取带token的json数据
        payload = recieveHeader.getJSONObject("payload");
        System.out.println(payload);
        String token = (String)payload.get("accessToken");
//        Claims claims = Jwts.parser()
//                .setSigningKey(DatatypeConverter.parseBase64Binary(base64Security))
//                .parseClaimsJws(token).getBody();
        System.out.println("flow=="+token);
        return token;
    }


    /**
     * 获取设备列表
     * @param str
     * @return
     */
    private JSONObject getMerchineList(String str , List<ProductDevice> productDeviceList){
        //要返回的设备列表
        JSONObject MerchineList = new JSONObject();
        //保存返回的header信息
        JSONObject header = new JSONObject();
        // 设备对象
        JSONObject payload = new JSONObject();
        //设备列表
        List<JSONObject> devices =  new ArrayList();
        //页面传过来的的JSON参数信息
        JSONObject recieveHeader = new JSONObject();
        recieveHeader = JSON.parseObject(str);
        //获取header参数
        String str1 = recieveHeader.getString("header");
//        System.out.println("header:" + recieveHeader.getString("header"));
        //header参数转为JSON参数对象
        JSONObject recieveMessageId = new JSONObject();
        recieveMessageId = JSON.parseObject(str1);
        System.out.println("messageId:" + recieveMessageId.getString("messageId"));

        header.put("namespace", "AliGenie.Iot.Device.Discovery");
        header.put("name", "DiscoveryDevicesResponse");
        header.put("messageId", recieveMessageId.getString("messageId"));
        header.put("payLoadVersion", "1");
        for(int i  = 0 ; i < productDeviceList.size() ; i ++ ){
            ProductDevice productDevice = productDeviceList.get(i);
            //添加设备信息
            JSONObject device = new JSONObject();
            JSONObject propertieIsopen = new JSONObject();
            JSONObject propertieOnLine = new JSONObject();
            List<JSON> properties = new ArrayList();
            List actions = new ArrayList();
            JSONObject extentions = new JSONObject();


//            设备ID
            device.put("deviceId", productDevice.getId() + "");
            //设备名称
            device.put("deviceName", productDevice.getAlias());
            //设备类型
            device.put("deviceType", "fan");
            //设备位置
            device.put("zone", "");
            //品牌
            device.put("brand", productDevice.getModel());
            //设备型号
            device.put("model", productDevice.getModel());
            //设备图片地址  产品icon(https协议的url链接),像素最好160*160 以免在app显示模糊
            device.put("icon", "https://ss0.bdstatic.com/94oJfD_bAAcT8t7mm9GUKT-xh_/timg?image&quality=100&size=b4000_4000&sec=1531878000&di=c989660f4b827a0049c3b7aec4fe38e1&src=http://img.czvv.com/sell/599adfe4d2f0b1b2f118606f/20170905113247194.jpg");


            //返回当前设备支持的属性状态列表，产品支持的属性列表参考 设备控制与设备状态查询页 的 第二部分 设备状态查询 2.2 章节
            //设备是否在线
//            propertieOnLine.put("name", "onlinestate");
//            propertieOnLine.put("value", productDevice.getIsOnline()==1?"online":"offline");
//            properties.add(propertieOnLine);
            //设备当前状态
//            propertieIsopen.put("name", "remotestatus");
//            propertieIsopen.put("value", productDevice.getIsOnline().equals("1")?"on":"off");
//            properties.add(propertieIsopen);
            propertieOnLine.put("name", "windspeed");
            propertieOnLine.put("value", "1");
            properties.add(propertieOnLine);
            device.put("properties", properties);

//            actions.add("TurnOn");
//            actions.add("TurnOff");
            actions.add("SetWindSpeed");
            device.put("actions", actions);

            extentions.put("extension1", "扩展1");
            extentions.put("extension2", "扩展2");
            device.put("extensions", extentions);
            devices.add(device);
        }
        payload.put("devices", devices);

        MerchineList.put("header", header);
        MerchineList.put("payload", payload);
        System.out.println("设备列表" + MerchineList);
        return MerchineList;
    }

    /**
     * 获取该设备可操作内容
     * @param str
     * @return
     */
    private JSONObject getMercherListTest (String str){
        JSONObject header = new JSONObject();
        // 设备对象
        JSONObject payload = new JSONObject();
        //要返回的设备列表
        JSONObject controllerList = new JSONObject();
        //扩展
        JSONObject extentions = new JSONObject();

        //头部文件设置
        header.put("namespace", "AliGenie.Iot.Device.Control");
        header.put("name", "SetWindSpeed");
        header.put("messageId", UUIDUtil.getUUID());
        header.put("payLoadVersion", "1");

        payload.put("accessToken", str);
        payload.put("deviceId", "1");
        payload.put("deviceType", "fan" );
        payload.put("attribute", "windspeed");
        payload.put("value", "1");
        extentions.put("extension1", "tset");
        extentions.put("extension2", "test");
        payload.put("extensions", extentions);


        controllerList.put("header", header);
        controllerList.put("payload", payload);
        System.out.println("控制参数" +controllerList.toString());
        return controllerList;
    }
}