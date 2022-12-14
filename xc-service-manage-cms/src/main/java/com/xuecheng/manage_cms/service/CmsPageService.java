package com.xuecheng.manage_cms.service;

import com.alibaba.fastjson.JSON;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.xuecheng.framework.domain.cms.CmsConfig;
import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.domain.cms.CmsSite;
import com.xuecheng.framework.domain.cms.CmsTemplate;
import com.xuecheng.framework.domain.cms.request.QueryPageRequest;
import com.xuecheng.framework.domain.cms.response.CmsCode;
import com.xuecheng.framework.domain.cms.response.CmsPageResult;
import com.xuecheng.framework.domain.cms.response.CmsPostPageResult;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.manage_cms.config.RabbitMQConfig;
import com.xuecheng.manage_cms.dao.CmsConfigRepository;
import com.xuecheng.manage_cms.dao.CmsPageRepository;
import com.xuecheng.manage_cms.dao.CmsSiteRepository;
import com.xuecheng.manage_cms.dao.CmsTemplateRepository;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class CmsPageService {

    @Autowired
    private CmsPageRepository pageRepository;
    @Autowired
    private CmsConfigRepository cmsConfigRepository;
    @Autowired
    private CmsTemplateRepository templateRepository;

    @Autowired
    private CmsSiteRepository cmsSiteRepository;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private GridFSBucket gridFSBucket;
    @Autowired
    private GridFsTemplate gridFsTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * @page ????????????1??????
     * @size ???????????????
     * @param queryPageRequest  ????????????
     */
    public QueryResponseResult findList(int page, int size, QueryPageRequest queryPageRequest) {
         if (queryPageRequest==null){
             queryPageRequest = new QueryPageRequest();
         }
         //?????????????????????
         //????????????????????????
        ExampleMatcher exampleMatcher = ExampleMatcher.matching()
                .withMatcher("pageAliase",
                        ExampleMatcher.GenericPropertyMatchers.contains());
         //???????????????
        CmsPage cmsPage = new CmsPage();
        //???????????????
        if (!StringUtils.isEmpty(queryPageRequest.getSiteId())){
            cmsPage.setSiteId(queryPageRequest.getSiteId());
        }
        if (!StringUtils.isEmpty(queryPageRequest.getTemplateId())){
            cmsPage.setTemplateId(queryPageRequest.getTemplateId());
        }
        if (!StringUtils.isEmpty(queryPageRequest.getPageAliase())){
            cmsPage.setPageAliase(queryPageRequest.getPageAliase());
        }
        //??????????????????
        Example<CmsPage> example = Example.of(cmsPage,exampleMatcher);

        if (page<=0){
            page=1;
        }
        page = page-1;
        if (size<=0){
            size=10;
        }
        //????????????
        Pageable pageable = PageRequest.of(page,size);
        Page<CmsPage> list = pageRepository.findAll(example,pageable);//????????????
        QueryResult queryResult = new QueryResult();
        queryResult.setList(list.getContent());
        queryResult.setTotal(list.getTotalElements());
        QueryResponseResult queryResponseResult =
                new QueryResponseResult(CommonCode.SUCCESS, queryResult);
        return queryResponseResult;

    }

    /**
     * ????????????
     * @param cmsPage ???????????????????????????
     * @return
     */
    public CmsPageResult addPage(CmsPage cmsPage){
        //????????????????????????????????????????????????siteId,pageName,pageWebPath????????????
        CmsPage page = pageRepository.findBySiteIdAndAndPageNameAndPageWebPath(
                cmsPage.getSiteId(), cmsPage.getPageName(), cmsPage.getPageWebPath()
        );
        if (page == null){

            // ??????????????????????????????Spring Data??????????????????
            cmsPage.setPageId(null);
            pageRepository.save(cmsPage);
            return new CmsPageResult(CommonCode.SUCCESS,cmsPage);
        }
        return new CmsPageResult(CommonCode.FAIL, null);
    }

    /**
     * ??????id??????????????????
     * @param id
     * @return
     */
    public CmsPage findById(String id){
        Optional<CmsPage> op = pageRepository.findById(id);
        if (op.isPresent()){
            CmsPage cmsPage = op.get();
            return cmsPage;
        }
        return null;
    }

    /**
     * ????????????
     * @param id ????????????????????????id
     * @param cmsPage ???????????????????????????
     * @return
     */
    public CmsPageResult update(String id,CmsPage cmsPage){
        CmsPage cmsPage1 = this.findById(id);
        if (cmsPage1!=null){
            //????????????
            cmsPage1.setPageAliase(cmsPage.getPageAliase());
            cmsPage1.setSiteId(cmsPage.getSiteId());
            cmsPage1.setPageName(cmsPage.getPageName());
            cmsPage1.setTemplateId(cmsPage.getTemplateId());
            cmsPage1.setPageWebPath(cmsPage.getPageWebPath());
            cmsPage1.setPagePhysicalPath(cmsPage.getPagePhysicalPath());
            cmsPage1.setPageStatus(cmsPage.getPageStatus());
            cmsPage1.setDataUrl(cmsPage.getDataUrl());
            pageRepository.save(cmsPage1);
            return new CmsPageResult(CommonCode.SUCCESS,cmsPage1);
        }
        return new CmsPageResult(CommonCode.FAIL,null);
    }

    /**
     * ????????????
     * @param id
     * @return
     */
    public ResponseResult delete(String id){
        //?????????
        Optional<CmsPage> c = pageRepository.findById(id);
        if (c.isPresent()){
            pageRepository.deleteById(id);
            return new ResponseResult(CommonCode.SUCCESS);
        }
        return new ResponseResult(CommonCode.FAIL);
    }

    /**
     * ??????id??????cms???????????????
     * @param id
     * @return
     */
    public CmsConfig getConfigById(String id){
        Optional<CmsConfig> optional = cmsConfigRepository.findById(id);
        if (optional.isPresent()){
            CmsConfig cmsConfig = optional.get();
            return  cmsConfig;
        }
        return null;
    }

    /**
     * ??????????????????
     * @param pageId
     * @return
     * @throws IOException
     * @throws TemplateException
     */
    public String getPageHtml(String pageId) throws IOException, TemplateException {
        //??????????????????????????????dataUrl
        Map map = getModelByPageId(pageId);
        if (map==null){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_DATAISNULL);
        }
        String template = getTemplateByPageId(pageId);
        if (template==null){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_TEMPLATEISNULL);
        }
        log.debug("::??????????????????==>>");
        //????????????????????????
        return generateHtml(template,map);
    }

    //??????????????????
    private String generateHtml(String template, Map map) throws IOException, TemplateException {
        //????????????
        Configuration configuration = new Configuration(Configuration.getVersion());

        //?????????????????????
        StringTemplateLoader templateLoader = new StringTemplateLoader();
        templateLoader.putTemplate("template",template);
        configuration.setTemplateLoader(templateLoader);
        Template template1 = configuration.getTemplate("template", "UTF_8");
        //???????????????????????????
        String html = FreeMarkerTemplateUtils.processTemplateIntoString(template1, map);
        return html;
    }

    //???????????????????????????
    private String getTemplateByPageId(String pageId){
        //?????????????????????
        CmsPage cmsPage = this.findById(pageId);
        if (cmsPage==null){
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        String templateId = cmsPage.getTemplateId();
        if (templateId==null){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_TEMPLATEISNULL);
        }
        //??????tmplateId????????????????????????????????????
        Optional<CmsTemplate> optional = templateRepository.findById(templateId);
        if (optional.isPresent()){
            CmsTemplate cmsTemplate = optional.get();
            String templateFileId = cmsTemplate.getTemplateFileId();
            //??????????????????TemplateFileId??????gridFS?????????????????????
            GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(templateFileId)));

            //?????????????????????
            GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(file.getObjectId());
            //
            GridFsResource gridFsResource = new GridFsResource(file,gridFSDownloadStream);

            try {
                String content = IOUtils.toString(gridFsResource.getInputStream(), "UTF-8");
                return content;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }finally {

            }
        }
        return null;
    }

    //?????????????????????
    private Map getModelByPageId(String pageId){
        //?????????????????????
        CmsPage cmsPage = this.findById(pageId);
        if (cmsPage==null){
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        //??????????????????dataUrl
        String dataUrl = cmsPage.getDataUrl();
        if (StringUtils.isEmpty(dataUrl)){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_DATAURLISNULL);
        }
        //??????restTemplate??????dataUrl????????????
        ResponseEntity<Map> forEntity = restTemplate.getForEntity(dataUrl, Map.class);
        if (forEntity != null) {
            Map body = forEntity.getBody();
            return body;
        }
        return null;
    }

    /**
     * ??????????????????
     *
     * @param pageId
     * @return
     * @throws Exception
     */
    public ResponseResult post(String pageId) {
        //???????????????
        String html = null;
        try {
            html = this.getPageHtml(pageId);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TemplateException e) {
            log.error(e.getMessage(),"????????????");
            e.printStackTrace();
        }
        if (StringUtils.isEmpty(html)){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_HTMLISNULL);
        }
        //??????????????????????????????GridFS
        CmsPage page = saveHtml(pageId,html);
        //???rabbitMQ????????????
        sendPostPage(pageId);
        return new ResponseResult(CommonCode.SUCCESS);

    }
    /* ???????????????GridFS */
    private CmsPage saveHtml(String pageId, String html) {
        //????????????
        Optional<CmsPage> optional = pageRepository.findById(pageId);
        if (!optional.isPresent()){
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        CmsPage cmsPage = optional.get();
        //?????????????????????
        String htmlFileId = cmsPage.getHtmlFileId();
        if (StringUtils.isNotEmpty(htmlFileId)){
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(htmlFileId)));
        }
        //????????????
        InputStream is = IOUtils.toInputStream(html);
        ObjectId objectId = gridFsTemplate.store(is, cmsPage.getPageName());
        //??????id
        String fileId = objectId.toString();
        //?????????id???????????????
        cmsPage.setHtmlFileId(fileId);
        pageRepository.save(cmsPage);
        return cmsPage;
    }
    /* ???????????????rabbitMQ */
    private void sendPostPage(String pageId) {
        CmsPage cmsPage = this.findById(pageId);
        if (cmsPage==null){
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        Map<String,String> msgMap = new HashMap();
        msgMap.put("pageId",pageId);
        String msg = JSON.toJSONString(msgMap);
        String siteId = cmsPage.getSiteId();
        //????????????
        this.rabbitTemplate.convertAndSend(RabbitMQConfig.EX_ROUTING_CMS_POSTPAGE,siteId,msg);
    }

    /**
     * ???????????????????????????
     * @param cmsPage
     * @return
     */
    public CmsPageResult save(CmsPage cmsPage) {
        //?????????????????????????????????????????????????????????siteId,pageName,pageWebPath????????????
        CmsPage page = pageRepository.findBySiteIdAndAndPageNameAndPageWebPath(
                cmsPage.getSiteId(), cmsPage.getPageName(), cmsPage.getPageWebPath()
        );
        if (page != null) {
            // ?????????????????????????????????
            return this.update(page.getPageId(), cmsPage);
        }else{
            // ??????????????????????????????
            return this.addPage(cmsPage);
        }
    }

    /**
     * ???????????????????????????
     * @return
     */
    public CmsPostPageResult postPageQuick(CmsPage cmsPage) {
        // ????????? save ??????????????????????????????
        CmsPageResult save = this.save(cmsPage);
        if(!save.isSuccess()){
            return new CmsPostPageResult(CommonCode.FAIL,null);
        }
        CmsPage cmsp = save.getCmsPage();
        // ???????????????id
        String pageId = cmsp.getPageId();
        // ????????????
        ResponseResult post = this.post(pageId);
        if(!post.isSuccess()){
            return new CmsPostPageResult(CommonCode.FAIL,null);
        }
        // ???????????????url
        // ?????????url = ???????????????+?????????webpath+?????????webpath+?????????
        // ??????ID
        String siteId = cmsp.getSiteId();
        // ????????????
        CmsSite site = findCmsSiteById(siteId);
        // ??????????????????
        String siteDomain = site.getSiteDomain();
        // ?????????web??????
        String siteWebpath = site.getSiteWebPath();
        // ?????????web??????
        String pageWebpath = cmsp.getPageWebPath();
        // ???????????????
        String pageName = cmsp.getPageName();
        // ?????????????????????
        String url = siteDomain + siteWebpath + pageWebpath + pageName;
        return new CmsPostPageResult(CommonCode.SUCCESS,url);

    }

    // ??????ID?????????????????????
    private CmsSite findCmsSiteById(String siteId) {

        Optional<CmsSite> optional = cmsSiteRepository.findById(siteId);
        return optional.orElse(null);
    }
}
