package com.cj.matchengine.api.rest;

import com.alibaba.fastjson.JSON;
import com.cj.engine.cfg.SpringAppContext;
import com.cj.engine.core.*;
import com.cj.engine.core.cfg.BasePatternConfig;
import com.cj.engine.model.MatchInfo;
import com.cj.engine.model.MatchPatternInfo;
import com.cj.engine.storage.*;
import com.cj.engine.transfomers.DocTransformer;
import com.cj.matchengine.api.rest.request.EnrollerRequest;
import com.cj.matchengine.api.rest.request.PatternRequest;
import com.cj.matchengine.api.rest.request.PreviewScheduleRequest;
import com.cj.matchengine.api.rest.result.*;
import com.cj.matchengine.api.rest.request.MatchRequest;
import com.google.common.base.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * <p>Create Time: 2018年03月01日</p>
 * <p>@author tangxd</p>
 **/
@RestController
@RequestMapping("v1")
public class RestApiController {

    @Autowired
    private IMatchStorage matchStorage;

    @Autowired
    private IPatternStorage patternStorage;

    @Autowired
    private IMatchRoundStorage roundStorage;

    @Autowired
    private IVsGroupStorage groupStorage;

    @Autowired
    private IVsNodeStorage nodeStorage;

    @Autowired
    private IMatchVsStorage vsStorage;

    @Autowired
    private IEnrollPlayerStorage playerStorage;

    @Autowired
    private DocTransformer<MatchInfo, MatchDto> matchTransformer;

    @Autowired
    private DocTransformer<MatchPatternInfo, PatternDto> patternTransformer;

    @Autowired
    private DocTransformer<MatchRound, RoundDto> roundTransformer;

    @Autowired
    private DocTransformer<VsGroup, GroupDto> groupTransformer;

    @Autowired
    private DocTransformer<VsNode, NodeDto> nodeTransformer;

    @Autowired
    private DocTransformer<MatchVs, VsDto> vsTransformer;

    @Autowired
    private DocTransformer<EnrollPlayer, EnrollerDto> enrollerTransformer;

    @Autowired
    private PatternConfigLoader cfgLoader;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //获取
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @RequestMapping(value = "match/pattern/{id}", method = RequestMethod.GET)
    public TResult<PatternDto> getMatchPatternData(@PathVariable("id") int id) {
        if (id <= 0) {
            return new TResult<>("MP:0001", "id非法", null);
        }
        MatchPatternInfo pInfo = patternStorage.get(id);
        if (null == pInfo) {
            return new TResult<>("MP:0002", "模型不存在", null);
        }
        PatternDto dto = patternTransformer.transform(pInfo);
        return new TResult<>(Result.SUCCESS, "成功", dto);
    }

    @RequestMapping(value = "match/round/{id}", method = RequestMethod.GET)
    public TResult<RoundDto> getMatchRoundData(@PathVariable("id") String id) {
        if (Strings.isNullOrEmpty(id)) {
            return new TResult<>("MP:1001", "id非法", null);
        }
        MatchRound round = roundStorage.get(id);
        if (null == round) {
            return new TResult<>("MP:1002", "轮次不存在", null);
        }
        RoundDto dto = roundTransformer.transform(round);
        return new TResult<>(Result.SUCCESS, "成功", dto);
    }

    @RequestMapping(value = "match/group/{id}", method = RequestMethod.GET)
    public TResult<GroupDto> getMatchGroupData(@PathVariable("id") String id) {
        if (Strings.isNullOrEmpty(id)) {
            return new TResult<>("MP:1201", "id非法", null);
        }
        VsGroup group = groupStorage.get(id);
        if (null == group) {
            return new TResult<>("MP:1202", "小组不存在", null);
        }
        GroupDto dto = groupTransformer.transform(group);
        return new TResult<>(Result.SUCCESS, "成功", dto);
    }

    @RequestMapping(value = "match/node/{id}", method = RequestMethod.GET)
    public TResult<NodeDto> getMatchNodeData(@PathVariable("id") String id) {
        if (Strings.isNullOrEmpty(id)) {
            return new TResult<>("MP:1301", "id非法", null);
        }
        VsNode node = nodeStorage.get(id);
        if (null == node) {
            return new TResult<>("MP:1302", "成员不存在", null);
        }
        NodeDto dto = nodeTransformer.transform(node);
        return new TResult<>(Result.SUCCESS, "成功", dto);
    }

    @RequestMapping(value = "match/vs/{id}", method = RequestMethod.GET)
    public TResult<VsDto> getMatchVsData(@PathVariable("id") int id) {
        if (id < 0) {
            return new TResult<>("MP:1401", "id非法", null);
        }
        MatchVs vs = vsStorage.get(id);
        if (null == vs) {
            return new TResult<>("MP:1402", "对阵不存在", null);
        }
        VsDto dto = vsTransformer.transform(vs);
        return new TResult<>(Result.SUCCESS, "成功", dto);
    }

    @RequestMapping(value = "match/enrollers/{match_id}", method = RequestMethod.GET)
    public TResult<Collection<EnrollerDto>> getEnrollers(@PathVariable("match_id") int matchId) {
        if (matchId < 0) {
            return new TResult<>("MP:1501", "id非法", null);
        }
        Collection<EnrollPlayer> players = playerStorage.getPlayers(matchId);
        Collection<EnrollerDto> dtos = new ArrayList<>();
        for (EnrollPlayer player : players) {
            dtos.add(enrollerTransformer.transform(player));
        }
        return new TResult<>(Result.SUCCESS, "成功", dtos);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //生成
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @RequestMapping(value = "enroller", method = RequestMethod.POST)
    public Result addEnroller(@RequestBody EnrollerRequest enroller, HttpServletRequest req) {
        if (Strings.isNullOrEmpty(enroller.getPlayerId())) {
            return new Result("MP:1901", "player_id未设置");
        }
        if (enroller.getMatchId() < 0) {
            return new Result("MP:1902", "match_id未设置");
        }
        if (enroller.getType() == null) {
            return new Result("MP:1903", "type未设置");
        }
        MatchInfo match = matchStorage.get(enroller.getMatchId());
        if (match == null) {
            return new Result("MP:1905", "赛事不存在");
        }
        if (match.getState() != MatchStates.UnInitialize
                || match.getState() != MatchStates.Initialized) {
            return new Result("MP:1906", "赛事状态不能添加报名ß");
        }

        EnrollPlayer player = new EnrollPlayer();
        player.setPlayerId(enroller.getPlayerId());
        player.setMatchId(enroller.getMatchId());
        player.setType(enroller.getType());
        player.setLevelId(enroller.getLevelId());
        if (enroller.getProperties() != null) {
            player.setProperties(JSON.toJSONString(enroller.getProperties()));
        }
        boolean ok = playerStorage.save(player);
        if (!ok) {
            return new Result("MP:1904", "保存未成功");
        }
        return new Result(Result.SUCCESS, "保存成功");
    }

    @RequestMapping(value = "match", method = RequestMethod.POST)
    public Result createMatch(@RequestBody MatchRequest match, HttpServletRequest req) {
        if (Strings.isNullOrEmpty(match.getTitle())) {
            return new Result("MP:1601", "title未设置");
        }
        MatchInfo m = new MatchInfo();
        m.setTitle(match.getTitle());
        if (match.getProperties() != null) {
            m.setProperties(JSON.toJSONString(match.getProperties()));
        }
        boolean ok = matchStorage.save(m);
        if (!ok) {
            return new Result("MP:1602", "保存未成功");
        }
        return new Result(Result.SUCCESS, "保存成功");
    }

    @RequestMapping(value = "pattern", method = RequestMethod.POST)
    public Result createPattern(@RequestBody PatternRequest pattern, HttpServletRequest req) {
        if (pattern.getMatchId() <= 0) {
            return new Result("MP:1702", "matchId未设置");
        }
        if (pattern.getType() == null) {
            return new Result("MP:1703", "type未设置");
        }
        if (pattern.getBo() <= 0) {
            return new Result("MP:1704", "bo未设置");
        }
        if (pattern.getPromotions() <= 0) {
            return new Result("MP:1705", "promotions必须大于0小于参赛人数");
        }
        if (pattern.getType() == PatternTypes.Group && pattern.getGroupPlayers() <= 0) {
            return new Result("MP:1706", "group_players必须大于0");
        }
        if (pattern.getIndex() < 0) {
            return new Result("MP:1707", "index必须大于等于0");
        }

        Collection<MatchPatternInfo> patternInfos = patternStorage.gets(pattern.getMatchId());
        for (MatchPatternInfo info : patternInfos) {
            if (info.getIndex() == pattern.getIndex()) {
                return new Result("MP:1708", String.format("已存在index=%d的赛制", pattern.getIndex()));
            }
        }

        MatchPatternInfo pi = new MatchPatternInfo();
        pi.setMatchId(pattern.getMatchId());
        pi.setType(pattern.getType());
        pi.setBoN(pattern.getBo());
        pi.setIndex(pattern.getIndex());
        pi.setPromotions(pattern.getPromotions());
        pi.setGroupPlayers(pattern.getGroupPlayers());
        if (pattern.getProperties() != null) {
            pi.setProperties(JSON.toJSONString(pattern.getProperties()));
        }
        if (Strings.isNullOrEmpty(pattern.getTitle())) {
            pi.setTitle(String.format("第%d阶段", pi.getIndex() + 1));
        } else {
            pi.setTitle(pattern.getTitle());
        }
        Optional<MatchPatternInfo> maxIndexPattern = patternInfos.stream().max(Comparator.comparing(MatchPatternInfo::getIndex));
        maxIndexPattern.ifPresent(matchPatternInfo -> pi.setPid(matchPatternInfo.getId()));

        boolean ok = patternStorage.save(pi);
        if (!ok) {
            return new Result("MP:1708", "保存未成功");
        }
        return new Result(Result.SUCCESS, "保存成功");
    }

    @RequestMapping(value = "match/build_schedule/{matchId}", method = RequestMethod.POST)
    public Result buildSchedule(@PathVariable("matchId") int matchId,
                                HttpServletRequest req) {
        if (matchId <= 0) {
            return new Result("MP:1801", "matchId非法");
        }

        MatchInfo match = matchStorage.get(matchId);
        if (match == null) {
            return new Result("MP:1803", "赛事不存在");
        }

        Collection<MatchPatternInfo> patterns = patternStorage.gets(matchId);
        if (patterns.size() == 0) {
            return new Result("MP:1804", "未设置赛事中的赛制模型");
        }

        int players = playerStorage.counts(matchId);
        if (players <= 0) {
            return new Result("MP:1806", "报名人数小于0");
        }

        IMatchEngine engine = SpringAppContext.getBean(IMatchEngine.class, matchId);
        engine.init();
        MResult mr = engine.buildSchedule(players);
        if (mr.getCode().equals(MResult.SUCCESS_CODE)) {
            engine.save();
            return new Result(Result.SUCCESS, "生成成功");
        }
        return new Result("MP:1805", "生成失败");
    }

    @RequestMapping(value = "match/build_schedule/preview", method = RequestMethod.POST)
    public TResult<Object> buildSchedulePreview(@RequestBody PreviewScheduleRequest pmsr,
                                                HttpServletRequest req) {
        if (pmsr.getMaxPlayers() <= 0) {
            return new TResult<>("MP:1901", "max_players未设置", null);
        }
        if (pmsr.getPatterns() == null || pmsr.getPatterns().size() == 0) {
            return new TResult<>("MP:1902", "未设置赛制", null);
        }
        short idx = 0;
        for (PatternRequest pr : pmsr.getPatterns()) {
            if (pr.getType() == null) {
                return new TResult<>("MP:1903", "type未设置", null);
            }
            if (pr.getIndex() != idx) {
                return new TResult<>("MP:1904", "index顺序错误", null);
            }
            if (pr.getPromotions() <= 0) {
                return new TResult<>("MP:1904", "promotions设置错误", null);
            }
            if (pr.getType() == PatternTypes.Group && pr.getGroupPlayers() <= 0) {
                return new TResult<>("MP:1905", "group_players设置错误", null);
            }
            idx++;
        }
        IMatchEngine engine = SpringAppContext.getBean(IMatchEngine.class, 0);
        engine.setIsPreview(true);
        engine.init();
        for (PatternRequest pr : pmsr.getPatterns()) {
            MatchPatternInfo info = new MatchPatternInfo();
            info.setGroupPlayers(pr.getGroupPlayers());
            info.setIndex(pr.getIndex());
            info.setPromotions(pr.getPromotions());
            info.setType(pr.getType());
            if (pr.getProperties() != null) {
                info.setProperties(JSON.toJSONString(pr.getProperties()));
            }
            BasePatternConfig cfg = cfgLoader.convert(info);
            engine.addPattern(cfg);
        }
        MResult mr = engine.buildSchedule(pmsr.getMaxPlayers());
        if (mr.getCode().equals(MResult.SUCCESS_CODE)) {
            AbstractMatchPattern pattern = engine.getFirstPattern();

            //转化输出
            return new TResult<>(Result.SUCCESS, "OK", pattern);
        }
        return null;
    }

    @RequestMapping(value = "match/schedule/{matchId}", method = RequestMethod.GET)
    public TResult<MatchDto> getSchedule(@PathVariable("matchId") int matchId,
                                         HttpServletRequest req) {
        if (matchId <= 0) {
            return new TResult<>("MP:2001", "matchId非法", null);
        }
        MatchInfo match = matchStorage.get(matchId);
        if (match == null) {
            return new TResult<>("MP:2002", "赛事不存在", null);
        }
        if (match.getState() != MatchStates.UnInitialize) {
            return new TResult<>("MP:2003", "赛事模型还未生成", null);
        }
        MatchDto dto = matchTransformer.transform(match);
        dto.setPatterns(new ArrayList<>());
        for (Integer patternId : dto.getPatternIds()) {
            MatchPatternInfo mpi = patternStorage.get(patternId);
            if (mpi != null) {
                PatternDto pDto = patternTransformer.transform(mpi);
                dto.getPatterns().add(pDto);
            }
        }
        return new TResult<>(Result.SUCCESS, "成功获取赛事模型", dto);
    }

    @RequestMapping(value = "match/assign_player/{matchId}", method = RequestMethod.POST)
    public Result assignPlayer(@PathVariable("matchId") int matchId,
                               HttpServletRequest req) {
        if (matchId <= 0) {
            return new Result("MP:2101", "matchId非法");
        }
        MatchInfo match = matchStorage.get(matchId);
        if (match == null) {
            return new Result("MP:2102", "赛事不存在");
        }
        if (match.getState() != MatchStates.Initialized) {
            return new Result("MP:2103", "当前赛事模型不能分配选手");
        }
        IMatchEngine engine = SpringAppContext.getBean(IMatchEngine.class, matchId);
        engine.init();
        MResult result = engine.assignPlayers();
        if (result.getCode().equals(MResult.SUCCESS_CODE)) {
            return new Result(Result.SUCCESS, "成功分配选手");
        }
        return new Result("MP:2104", result.getMessage());
    }
}
