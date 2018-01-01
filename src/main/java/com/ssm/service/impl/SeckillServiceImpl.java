package com.ssm.service.impl;

import com.ssm.dao.SecKillDao;
import com.ssm.dao.SuccessKilledDao;
import com.ssm.dao.cache.RedisDao;
import com.ssm.dto.Exposer;
import com.ssm.dto.SeckillExecution;
import com.ssm.entity.SecKill;
import com.ssm.entity.SuccessKilled;
import com.ssm.enums.SeckillStatEnum;
import com.ssm.exception.RepeatKillException;
import com.ssm.exception.SeckillCloseException;
import com.ssm.exception.SeckillException;
import com.ssm.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.List;

/**
 * service 接口实现
 */
@Service
public class SeckillServiceImpl implements SeckillService {
    //日志对象
    private Logger logger= LoggerFactory.getLogger(this.getClass());
    //加入一个混淆字符串(秒杀接口)的salt，为了我避免用户猜出我们的md5值，值任意给，越复杂越好
    private final String salt="shsdssljdd'l.";
    @Autowired
    private SecKillDao secKillDao;              //秒杀商品dao
    @Autowired
    private SuccessKilledDao successKilledDao;  //秒杀成功列表dao
    @Autowired
    private RedisDao redisDao;                  //redis缓存



    /**
     * 查询所有秒杀列表
     * @return
     */
    public List<SecKill> getSeckillList() {

        return secKillDao.queryAll(0,4);
    }

    /**
     * 根据Id查询
     * @param seckillId
     * @return
     */
    public SecKill getById(long seckillId) {

        return secKillDao.queryById(seckillId);
    }
    /**
     * 暴露秒杀接口
     * @param seckillId
     * @return
     */
    public Exposer exportSeckillUrl(long seckillId) {

        //优化点:缓存优化:超时的基础上维护一致性
        //1。访问redi


        SecKill seckill = redisDao.getSeckill(seckillId);
        if (seckill == null) {
            //2.访问数据库
            seckill = secKillDao.queryById(seckillId);
            if (seckill == null) {//说明查不到这个秒杀产品的记录
                return new Exposer(false, seckillId);
            }else {
                //3,放入redis
                redisDao.putSeckill(seckill);
            }

        }


        //若是秒杀未开启
        Date startTime=seckill.getStartTime();
        Date endTime=seckill.getEndTime();
        //系统当前时间
        Date nowTime=new Date();
        if (startTime.getTime()>nowTime.getTime() || endTime.getTime()<nowTime.getTime())
        {
            return new Exposer(false,seckillId,nowTime.getTime(),startTime.getTime(),endTime.getTime());
        }

        //秒杀开启，返回秒杀商品的id、用给接口加密的md5
        String md5=getMD5(seckillId);
        return new Exposer(true,md5,seckillId);
    }
    /**
     * 执行秒杀
     * @param seckillId
     * @return
     */
    public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5) throws SeckillException, RepeatKillException, SeckillCloseException {


        if (md5==null||!md5.equals(getMD5(seckillId)))
        {
            throw new SeckillException("seckill data rewrite");//秒杀数据被重写了
        }
        //执行秒杀逻辑:减库存+增加购买明细
        Date nowTime=new Date();

        try{

            //否则更新了库存，秒杀成功,增加明细
            int insertCount=successKilledDao.insertSuccessKilled(seckillId,userPhone);
            //看是否该明细被重复插入，即用户是否重复秒杀
            if (insertCount<=0)
            {
                throw new RepeatKillException("seckill repeated");
            }else {

                //减库存,热点商品竞争
                int updateCount=secKillDao.reduceNumber(seckillId,nowTime);
                if (updateCount<=0)
                {
                    //没有更新库存记录，说明秒杀结束 rollback
                    throw new SeckillCloseException("seckill is closed");
                }else {
                    //秒杀成功,得到成功插入的明细记录,并返回成功秒杀的信息 commit
                    SuccessKilled successKilled=successKilledDao.queryByIdWithSeckill(seckillId,userPhone);
                    return new SeckillExecution(seckillId, SeckillStatEnum.SUCCESS,successKilled);
                }

            }


        }catch (SeckillCloseException e1)
        {
            throw e1;
        }catch (RepeatKillException e2)
        {
            throw e2;
        }catch (Exception e)
        {
            logger.error(e.getMessage(),e);
            //所以编译期异常转化为运行期异常
            throw new SeckillException("seckill inner error :"+e.getMessage());
        }
    }

    private String getMD5(long seckillId)
    {
        String base=seckillId+"/"+salt;
        String md5= DigestUtils.md5DigestAsHex(base.getBytes());
        return md5;
    }
}
