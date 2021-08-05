package net.polyv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import net.polyv.common.v1.base.HttpUtil;
import net.polyv.common.v1.exception.PloyvSdkException;
import net.polyv.vod.v1.config.VodGlobalConfig;
import net.polyv.vod.v1.entity.manage.list.VodGetVideoListRequest;
import net.polyv.vod.v1.entity.manage.list.VodGetVideoListResponse;
import net.polyv.vod.v1.service.manage.impl.VodListServiceImpl;

/**
 * 查询出点播视频图片访问不到的vId
 * @author: sadboy
 **/
public class APP {
    //TODO 点播后台userId
    public static final String userId = "";
    //TODO 点播后台secretkey
    public static final String secretKey = "";
    //点播视频查询条件
    public static VodGetVideoListRequest vodGetVideoListRequest = new VodGetVideoListRequest();
    
    public static String errorVId = "";
    
    public static void main(String[] args) throws IOException {
        long currentTimeMillis = System.currentTimeMillis();
        //初始化连接池和用户信息
        VodGlobalConfig.init(userId, secretKey);
        
        //设置点播视频查询参数(防止循环查询视频过多，对后台产生影响)
        vodGetVideoListRequest.setFilters("basicInfo")
                //TODO 视频太多的情况下建议按分类搜索减少数量
                .setCategoryId(null).setStatus("60,61").setContainSubCate(true).setPageSize(100);
        //查询出所有视频id
        List<String> queryVideo = queryVideo(1);
        System.out.println("当前查询出视频数为：" + queryVideo.size());
        
        for (String tempVid : queryVideo) {
            if (checkVodVId(tempVid)) {
                errorVId += tempVid + ",";
            }
        }
        System.out.println("错误视频封面id：" + errorVId);
//        FileUtil.writeFile(errorVId.getBytes(StandardCharsets.UTF_8),"D:\\errorVid.txt");
        System.out.println("耗时：" + (System.currentTimeMillis() - currentTimeMillis) / 1000);
    }
    
    /**
     * 循环查询点播视频，直到查询出所有的点播视频，
     * @param currentPage
     * @return
     */
    public static List<String> queryVideo(int currentPage) {
        List<String> vidList = new ArrayList<>();
        VodGetVideoListResponse vodGetVideoListResponse = null;
        try {
            vodGetVideoListRequest.setCurrentPage(currentPage).setSign(null);
            vodGetVideoListResponse = new VodListServiceImpl().getVideoList(vodGetVideoListRequest);
            if (vodGetVideoListResponse != null) {
                for (VodGetVideoListResponse.VodGetVideoList videoList : vodGetVideoListResponse.getContents()) {
                    vidList.add(videoList.getVideoId());
                }
                if (vodGetVideoListResponse.getTotalPage() > vodGetVideoListResponse.getCurrentPage()) {
                    Thread.sleep(500);
                    vidList.addAll(queryVideo(currentPage + 1));
                }
            }
        } catch (PloyvSdkException e) {
            //参数校验不合格 或者 请求服务器端500错误，错误信息见PloyvSdkException.getMessage()
            // 异常返回做B端异常的业务逻辑，记录log 或者 上报到ETL 或者回滚事务
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return vidList;
    }
    
    /**
     * 传入点播视频id，返回该视频的图片是否存在问题
     * 已删除视频直接跳过
     * @param v_id 视频id，如：1b448be3239be11b5a206f26e3baa988_1
     * @throws IOException
     */
    private static boolean checkVodVId(String v_id) throws IOException {
        String s = HttpUtil.get("https://player.polyv.net/videojson/" + v_id + ".js");
        s = s.replace(",,", ",");
        JSONObject jsonObject = JSON.parseObject(s);
        Integer status = jsonObject.getInteger("status");
        if (status != null) {
            switch (status) {
                case -1:
                    System.out.println("视频已删除");
                    break;
                default:
                    String imgUrl = jsonObject.getString("first_image_b");
                    int code = HttpUtil.getWebCode(imgUrl);
                    if (code != 200) {
                        System.out.println("当前视频有问题" + v_id);
                        return true;
                    }
                    break;
            }
            return false;
        }
        return true;
    }
    
}
