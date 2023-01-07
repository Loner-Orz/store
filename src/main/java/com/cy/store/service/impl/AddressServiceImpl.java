package com.cy.store.service.impl;

import com.cy.store.entity.Address;
import com.cy.store.mapper.AddressMapper;
import com.cy.store.service.IAddressService;
import com.cy.store.service.IDistrictService;
import com.cy.store.service.ex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/** 新增收货地址的实现类 */
@Service
public class AddressServiceImpl implements IAddressService {
    @Autowired
    private AddressMapper addressMapper;
    //在添加用户的收货地址的业务层依赖于DistirctService的业务层
    @Autowired
    private IDistrictService districtService;

    @Value("${user.address.max-count}")
    private Integer maxCount;

    @Override
    public void addNewAddress(Integer uid, String username, Address address) {
        //调用收货地址统计的方法
        Integer count = addressMapper.countByUid(uid);
        if (count >= maxCount){
            throw new AddressCountLimitException("用户收货地址超出限制");
        }

        //对address对象中的数据进行补全：省市区
        String provinceName = districtService.getNameByCode(address.getProvinceCode());
        String cityName = districtService.getNameByCode(address.getCityCode());
        String areaName = districtService.getNameByCode(address.getAreaCode());
        address.setProvinceName(provinceName);
        address.setCityName(cityName);
        address.setAreaName(areaName);

        //uid,isDefault
        address.setUid(uid);
        Integer isDefault = count == 0? 1:0;//1表示默认，0表示不默认
        address.setIsDefault(isDefault);
        //补全4项日志
        address.setCreatedUser(username);
        address.setCreatedTime(new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        address.setModifiedUser(username);
        address.setModifiedTime(new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());

        //插入收货地址的方法
        Integer rows = addressMapper.insert(address);
        if (rows != 1){
            throw new InsertException("插入收货地址产生未知的异常");
        }
    }

    @Override
    public List<Address> getByUid(Integer uid) {
        List<Address> list = addressMapper.findByUid(uid);
        for (Address address : list){
            //address.setAid(null);
            //address.setUid(null);
            address.setProvinceCode(null);
            address.setCityCode(null);
            address.setAreaCode(null);
            address.setTel(null);
            address.setIsDefault(null);
            address.setCreatedUser(null);
            address.setCreatedTime(null);
            address.setModifiedUser(null);
            address.setModifiedTime(null);
        }
        return list;
    }

    @Override
    public void setDefault(Integer aid, Integer uid, String username) {
        Address result = addressMapper.findByAid(aid);
        if (result == null){
            throw new AddressNotFoundException("收货地址不存在");
        }
        //检测当前获取到的收货地址的归属
        if (!result.getUid().equals(uid)){
            throw new AccessDeniedException("非法数据访问");
        }
        //先将所有的收货地址设置为非默认
        Integer rows = addressMapper.updateNonDefault(uid);
        if (rows < 1){
            throw new UpdateException("更新数据产生未知异常");
        }
        //将用户选中的数据设置为默认地址
        rows = addressMapper.updateDefaultByAid(aid,
                                                username,
                                                new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            if (rows != 1){
            throw new UpdateException("更新数据产生未知异常");
        }
    }

    @Override
    public void deleteAddress(Integer aid, Integer uid, String username) {
        //检测地址是否存在
        Address result = addressMapper.findByAid(aid);
        if (result == null){
            throw new AddressNotFoundException("收货地址不存在");
        }
        //检测当前获取到的收货地址的归属
        if (!result.getUid().equals(uid)){
            throw new AccessDeniedException("非法数据访问");
        }
        //删除用户选中的收货地址
        Integer rows = addressMapper.deleteByAid(aid);
        if (rows != 1){
            throw new DeleteException("删除收货地址时产生未知的异常");
        }
        //用户地址总数是否为0
        Integer count = addressMapper.countByUid(uid);
        if (count == 0){
            return;
        }
        if(result.getIsDefault() == 0){
            return;
        }
        //如果当前要删除的收货地址是默认收货地址，则需要将最新修改的地址设置为默认收货地址
        Address address = addressMapper.findLastModified(uid);
        rows = addressMapper.updateDefaultByAid(address.getAid(),
                                         username,
                                         new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        if (rows != 1){
            throw new UpdateException("更新数据时产生未知的异常");
        }
    }


    @Override
    public void updateAddress(Integer aid,Integer uid,Address address,String username ) {
        //检测地址是否存在
        Address result = addressMapper.findByAid(aid);
        if (result == null){
            throw new AddressNotFoundException("收货地址不存在");
        }
        //检测当前获取到的收货地址的归属
        if (!result.getUid().equals(uid)){
            throw new AccessDeniedException("非法数据访问");
        }
        //对address对象中的数据进行补全：省市区
        address.setProvinceName(districtService.getNameByCode(address.getProvinceCode()));
        address.setCityName(districtService.getNameByCode(address.getCityCode()));
        address.setAreaName(districtService.getNameByCode(address.getAreaCode()));

        //补全表单中没有的字段
        address.setAid(aid);
        address.setUid(uid);
        address.setModifiedUser(username);
        address.setModifiedTime(new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());

        //更新地址信息
        Integer rows = addressMapper.updateAddress(address);
        if (rows == 0){
            throw new UpdateException("修改收货地址信息时产生未知的异常");
        }
    }

    @Override
    public Address queryAddressByAid(Integer aid) {
        return addressMapper.findByAid(aid);
    }


}
