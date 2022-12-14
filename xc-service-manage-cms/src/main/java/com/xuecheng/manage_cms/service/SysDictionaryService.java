package com.xuecheng.manage_cms.service;

import com.xuecheng.framework.domain.system.SysDictionary;
import com.xuecheng.manage_cms.dao.SysDictionaryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SysDictionaryService {

    @Autowired
    public SysDictionaryDao sysDictionaryDao;

    public SysDictionary getByType(String dType){

        return sysDictionaryDao.findBydType(dType);
    }

}
