package cn.itcast.service.impl;

import cn.itcast.constant.MessageConstant;
import cn.itcast.dao.SetMealDao;
import cn.itcast.entity.PageResult;
import cn.itcast.entity.Result;
import cn.itcast.pojo.CheckGroup;
import cn.itcast.pojo.Setmeal;
import cn.itcast.service.SetMealService;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service(interfaceClass = SetMealService.class)
@Transactional
public class SetMealServiceImpl implements SetMealService {

    @Autowired
    private SetMealDao setMealDao;

    @Autowired
    private JedisPool jedisPool;

    /**
     * 分页查询套餐
     * @param currentPage
     * @param pageSize
     * @param queryString
     * @return
     */
    @Override
    public Result findPage(Integer currentPage, Integer pageSize, String queryString) {
        try {
            PageHelper.startPage(currentPage,pageSize);
            Page<Setmeal> meals = setMealDao.selectSetMealByCondition(queryString);
            return new Result(true, MessageConstant.QUERY_SETMEAL_SUCCESS,new PageResult(meals.getTotal(),meals.getResult()));
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(false,MessageConstant.QUERY_CHECKGROUP_FAIL,null);
        }
    }

    @Override
    public Result findAllCheckGroup() {
        try {
            List<CheckGroup> checkGroups =  setMealDao.selectAllCheckGroup();
            return new Result(true,MessageConstant.QUERY_CHECKGROUP_SUCCESS,checkGroups);
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(false,MessageConstant.QUERY_CHECKGROUP_FAIL,null);
        }
    }

    /**
     * 添加套餐
     * @param setmeal
     * @param checkgroupIds
     * @return
     */
    @Override
    public Result addSetmeal(Setmeal setmeal, Integer[] checkgroupIds) {
        setMealDao.insertSetmeal(setmeal);
        addCheckgroupIdsOfSetmeal(checkgroupIds,setmeal.getId());
        return null;
    }

    @Override
    public Result getSetmeals() {
        try {
            Jedis jedis = jedisPool.getResource();
            String list = jedis.get("setmealList");
            if (list == null) {
                //从数据库查询
                List<Setmeal> setmeals = setMealDao.findAllSetmeals();
                //setmeals转为字符串，存入到redis中
                jedis = jedisPool.getResource();
                String str = JSON.toJSONString(setmeals);
                String setmealList = jedis.set("setmealList", str);
                return new Result(true, MessageConstant.QUERY_SETMEAL_SUCCESS,setmeals);
            }
            List<Setmeal> setmealList = JSON.parseArray(list, Setmeal.class);
            return new Result(true, MessageConstant.QUERY_SETMEAL_SUCCESS,setmealList);
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(false,MessageConstant.QUERY_SETMEAL_FAIL);
        }
    }

    /**
     * 根据id查询套餐详情
     * @param id
     * @return
     */
    @Override
    public Result findSetmealDetailById(Integer id) {
        try {
            Jedis jedis = jedisPool.getResource();
            Integer checkgroup = id;
            //判断redis获取的key是否为null，如果为null则从数据库获取，从数据空获取后存入到redis缓存中
            String setmealId = jedis.get(checkgroup.toString());
            if (setmealId == null) {
                //从数据库中查询
                Setmeal setmeal = setMealDao.findSetmealDetailById(id);
                //将setmeal转为字符串
                String jsonStr= JSON.toJSONString(setmeal);
                jedis.set(checkgroup.toString(),jsonStr);
                return new Result(true,MessageConstant.QUERY_SETMEAL_SUCCESS,setmeal);
            }
            JSONObject  jsonObject = JSONObject.parseObject(setmealId);
            Map<String,Object> map = (Map<String,Object>)jsonObject;
            return new Result(true,MessageConstant.QUERY_SETMEAL_SUCCESS,map);
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(false,MessageConstant.QUERY_SETMEAL_FAIL,null);
        }
    }

    /**
     * 套餐id与检查组id插入中间表
     * @param checkgroupIds
     */
    public void addCheckgroupIdsOfSetmeal(Integer[] checkgroupIds,Integer id) {
        HashMap<String, Integer> map = new HashMap<>();
        for (Integer checkgroupId : checkgroupIds) {
            map.put("setmeal_id",id);
            map.put("checkgroup_id",checkgroupId);
            setMealDao.insertSetmealCheckgroupList(map);
        }

    }
}
