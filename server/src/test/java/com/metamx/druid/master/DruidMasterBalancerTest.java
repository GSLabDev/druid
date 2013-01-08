/*
* Druid - a distributed column store.
* Copyright (C) 2012  Metamarkets Group Inc.
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package com.metamx.druid.master;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MinMaxPriorityQueue;
import com.google.common.collect.Sets;
import com.metamx.druid.client.DataSegment;
import com.metamx.druid.client.DruidDataSource;
import com.metamx.druid.client.DruidServer;
import com.metamx.druid.shard.NoneShardSpec;
import junit.framework.Assert;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class DruidMasterBalancerTest
{
  private static final int MAX_SEGMENTS_TO_MOVE = 5;
  private DruidMaster master;
  private DruidServer druidServer1;
  private DruidServer druidServer2;
  private DruidServer druidServer3;
  private DruidServer druidServer4;
  private DataSegment segment1;
  private DataSegment segment2;
  private DataSegment segment3;
  private DataSegment segment4;
  Map<String, DataSegment> segments;
  private LoadQueuePeon peon;
  private DruidDataSource dataSource;

  @Before
  public void setUp() throws Exception
  {
    master = EasyMock.createMock(DruidMaster.class);
    druidServer1 = EasyMock.createMock(DruidServer.class);
    druidServer2 = EasyMock.createMock(DruidServer.class);
    druidServer3 = EasyMock.createMock(DruidServer.class);
    druidServer4 = EasyMock.createMock(DruidServer.class);
    segment1 = EasyMock.createMock(DataSegment.class);
    segment2 = EasyMock.createMock(DataSegment.class);
    segment3 = EasyMock.createMock(DataSegment.class);
    segment4 = EasyMock.createMock(DataSegment.class);
    peon = EasyMock.createMock(LoadQueuePeon.class);
    dataSource = EasyMock.createMock(DruidDataSource.class);

    DateTime start1 = new DateTime("2012-01-01");
    DateTime start2 = new DateTime("2012-02-01");
    DateTime version = new DateTime("2012-03-01");
    segment1 = new DataSegment(
        "datasource1",
        new Interval(start1, start1.plusHours(1)),
        version.toString(),
        Maps.<String, Object>newHashMap(),
        Lists.<String>newArrayList(),
        Lists.<String>newArrayList(),
        new NoneShardSpec(),
        11L
    );
    segment2 = new DataSegment(
        "datasource1",
        new Interval(start2, start2.plusHours(1)),
        version.toString(),
        Maps.<String, Object>newHashMap(),
        Lists.<String>newArrayList(),
        Lists.<String>newArrayList(),
        new NoneShardSpec(),
        7L
    );
    segment3 = new DataSegment(
        "datasource2",
        new Interval(start1, start1.plusHours(1)),
        version.toString(),
        Maps.<String, Object>newHashMap(),
        Lists.<String>newArrayList(),
        Lists.<String>newArrayList(),
        new NoneShardSpec(),
        4L
    );
    segment4 = new DataSegment(
        "datasource2",
        new Interval(start2, start2.plusHours(1)),
        version.toString(),
        Maps.<String, Object>newHashMap(),
        Lists.<String>newArrayList(),
        Lists.<String>newArrayList(),
        new NoneShardSpec(),
        8L
    );

    segments = new HashMap<String, DataSegment>();
    segments.put("segment1", segment1);
    segments.put("segment2", segment2);
    segments.put("segment3", segment3);
    segments.put("segment4", segment4);
  }

  @After
  public void tearDown() throws Exception
  {
    EasyMock.verify(master);
    EasyMock.verify(druidServer1);
    EasyMock.verify(druidServer2);
    EasyMock.verify(druidServer3);
    EasyMock.verify(druidServer4);
    EasyMock.verify(peon);
    EasyMock.verify(dataSource);
  }

  @Test
  public void testRun1()
  {
    // Mock some servers of different usages

    EasyMock.expect(druidServer1.getName()).andReturn("from").atLeastOnce();
    EasyMock.expect(druidServer1.getCurrSize()).andReturn(30L).atLeastOnce();
    EasyMock.expect(druidServer1.getMaxSize()).andReturn(100L).atLeastOnce();
    EasyMock.expect(druidServer1.getDataSources()).andReturn(Arrays.asList(dataSource)).anyTimes();
    EasyMock.expect(druidServer1.getSegments()).andReturn(segments).anyTimes();
    EasyMock.replay(druidServer1);

    EasyMock.expect(druidServer2.getName()).andReturn("to").atLeastOnce();
    EasyMock.expect(druidServer2.getTier()).andReturn("normal").anyTimes();
    EasyMock.expect(druidServer2.getCurrSize()).andReturn(0L).atLeastOnce();
    EasyMock.expect(druidServer2.getMaxSize()).andReturn(100L).atLeastOnce();
    EasyMock.expect(druidServer2.getSegments()).andReturn(new HashMap<String, DataSegment>()).anyTimes();
    EasyMock.expect(druidServer2.getDataSources()).andReturn(Arrays.asList(dataSource)).anyTimes();
    EasyMock.expect(
        druidServer2.getSegment(
            "datasource1_2012-01-01T00:00:00.000Z_2012-01-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer2.getSegment(
            "datasource1_2012-02-01T00:00:00.000Z_2012-02-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer2.getSegment(
            "datasource2_2012-01-01T00:00:00.000Z_2012-01-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer2.getSegment(
            "datasource2_2012-02-01T00:00:00.000Z_2012-02-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(druidServer2.getSegment("segment3")).andReturn(null).anyTimes();
    EasyMock.expect(druidServer2.getSegment("segment4")).andReturn(null).anyTimes();
    EasyMock.replay(druidServer2);
    EasyMock.replay(druidServer3);
    EasyMock.replay(druidServer4);

    // Mock a datasource
    EasyMock.expect(dataSource.getSegments()).andReturn(
        Sets.<DataSegment>newHashSet(
            segment1,
            segment2,
            segment3,
            segment4
        )
    ).anyTimes();
    EasyMock.replay(dataSource);

    // Mock stuff that the master needs
    master.moveSegment(
        EasyMock.<String>anyObject(),
        EasyMock.<String>anyObject(),
        EasyMock.<String>anyObject(),
        EasyMock.<LoadPeonCallback>anyObject()
    );
    EasyMock.expectLastCall().atLeastOnce();
    EasyMock.replay(master);

    EasyMock.expect(peon.getLoadQueueSize()).andReturn(0L).atLeastOnce();
    EasyMock.expect(peon.getSegmentsToLoad()).andReturn(Sets.<DataSegment>newHashSet()).atLeastOnce();
    EasyMock.replay(peon);

    DruidMasterRuntimeParams params =
        DruidMasterRuntimeParams.newBuilder()
                                .withDruidCluster(
                                    new DruidCluster(
                                        ImmutableMap.<String, MinMaxPriorityQueue<ServerHolder>>of(
                                            "normal",
                                            MinMaxPriorityQueue.orderedBy(DruidMasterBalancer.percentUsedComparator)
                                                               .create(
                                                                   Arrays.asList(
                                                                       new ServerHolder(druidServer1, peon),
                                                                       new ServerHolder(druidServer2, peon)
                                                                   )
                                                               )
                                        )
                                    )
                                )
                                .withLoadManagementPeons(ImmutableMap.of("from", peon, "to", peon))
                                .withAvailableSegments(segments.values())
                                .withMaxSegmentsToMove(MAX_SEGMENTS_TO_MOVE)
                                .build();

    params = new DruidMasterBalancer(master, new BalancerCostAnalyzer(new DateTime("2013-01-01"))).run(params);
    Assert.assertTrue(params.getMasterStats().getPerTierStats().get("movedCount").get("normal").get() > 0);
    Assert.assertTrue(params.getMasterStats().getPerTierStats().get("costChange").get("normal").get() > 0);
  }

  @Test
  public void testRun2()
  {
    // Mock some servers of different usages
    EasyMock.expect(druidServer1.getName()).andReturn("1").atLeastOnce();
    EasyMock.expect(druidServer1.getCurrSize()).andReturn(30L).atLeastOnce();
    EasyMock.expect(druidServer1.getMaxSize()).andReturn(100L).atLeastOnce();
    EasyMock.expect(druidServer1.getDataSources()).andReturn(Arrays.asList(dataSource)).anyTimes();
    EasyMock.expect(druidServer1.getSegments()).andReturn(segments).anyTimes();
    EasyMock.replay(druidServer1);

    EasyMock.expect(druidServer2.getName()).andReturn("2").atLeastOnce();
    EasyMock.expect(druidServer2.getTier()).andReturn("normal").anyTimes();
    EasyMock.expect(druidServer2.getCurrSize()).andReturn(0L).atLeastOnce();
    EasyMock.expect(druidServer2.getMaxSize()).andReturn(100L).atLeastOnce();
    EasyMock.expect(druidServer2.getSegments()).andReturn(new HashMap<String, DataSegment>()).anyTimes();
    EasyMock.expect(druidServer2.getDataSources()).andReturn(Arrays.asList(dataSource)).anyTimes();
    EasyMock.expect(
        druidServer2.getSegment(
            "datasource1_2012-01-01T00:00:00.000Z_2012-01-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer2.getSegment(
            "datasource1_2012-02-01T00:00:00.000Z_2012-02-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer2.getSegment(
            "datasource2_2012-01-01T00:00:00.000Z_2012-01-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer2.getSegment(
            "datasource2_2012-02-01T00:00:00.000Z_2012-02-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.replay(druidServer2);

    EasyMock.expect(druidServer3.getName()).andReturn("3").atLeastOnce();
    EasyMock.expect(druidServer3.getTier()).andReturn("normal").anyTimes();
    EasyMock.expect(druidServer3.getCurrSize()).andReturn(0L).atLeastOnce();
    EasyMock.expect(druidServer3.getMaxSize()).andReturn(100L).atLeastOnce();
    EasyMock.expect(druidServer3.getSegments()).andReturn(new HashMap<String, DataSegment>()).anyTimes();
    EasyMock.expect(druidServer3.getDataSources()).andReturn(Arrays.asList(dataSource)).anyTimes();
    EasyMock.expect(
        druidServer3.getSegment(
            "datasource1_2012-01-01T00:00:00.000Z_2012-01-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer3.getSegment(
            "datasource1_2012-02-01T00:00:00.000Z_2012-02-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer3.getSegment(
            "datasource2_2012-01-01T00:00:00.000Z_2012-01-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer3.getSegment(
            "datasource2_2012-02-01T00:00:00.000Z_2012-02-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.replay(druidServer3);

    EasyMock.expect(druidServer4.getName()).andReturn("4").atLeastOnce();
    EasyMock.expect(druidServer4.getTier()).andReturn("normal").anyTimes();
    EasyMock.expect(druidServer4.getCurrSize()).andReturn(0L).atLeastOnce();
    EasyMock.expect(druidServer4.getMaxSize()).andReturn(100L).atLeastOnce();
    EasyMock.expect(druidServer4.getSegments()).andReturn(new HashMap<String, DataSegment>()).anyTimes();
    EasyMock.expect(druidServer4.getDataSources()).andReturn(Arrays.asList(dataSource)).anyTimes();
    EasyMock.expect(
        druidServer4.getSegment(
            "datasource1_2012-01-01T00:00:00.000Z_2012-01-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer4.getSegment(
            "datasource1_2012-02-01T00:00:00.000Z_2012-02-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer4.getSegment(
            "datasource2_2012-01-01T00:00:00.000Z_2012-01-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.expect(
        druidServer4.getSegment(
            "datasource2_2012-02-01T00:00:00.000Z_2012-02-01T01:00:00.000Z_2012-03-01T00:00:00.000Z"
        )
    ).andReturn(null).anyTimes();
    EasyMock.replay(druidServer4);


    // Mock a datasource
    EasyMock.expect(dataSource.getSegments()).andReturn(
        Sets.<DataSegment>newHashSet(
            segment1,
            segment2,
            segment3,
            segment4
        )
    ).anyTimes();
    EasyMock.replay(dataSource);

    // Mock stuff that the master needs
    master.moveSegment(
        EasyMock.<String>anyObject(),
        EasyMock.<String>anyObject(),
        EasyMock.<String>anyObject(),
        EasyMock.<LoadPeonCallback>anyObject()
    );
    EasyMock.expectLastCall().atLeastOnce();
    EasyMock.replay(master);

    EasyMock.expect(peon.getLoadQueueSize()).andReturn(0L).atLeastOnce();
    EasyMock.expect(peon.getSegmentsToLoad()).andReturn(Sets.<DataSegment>newHashSet()).atLeastOnce();
    EasyMock.replay(peon);

    DruidMasterRuntimeParams params =
        DruidMasterRuntimeParams.newBuilder()
                                .withDruidCluster(
                                    new DruidCluster(
                                        ImmutableMap.<String, MinMaxPriorityQueue<ServerHolder>>of(
                                            "normal",
                                            MinMaxPriorityQueue.orderedBy(DruidMasterBalancer.percentUsedComparator)
                                                               .create(
                                                                   Arrays.asList(
                                                                       new ServerHolder(druidServer1, peon),
                                                                       new ServerHolder(druidServer2, peon),
                                                                       new ServerHolder(druidServer3, peon),
                                                                       new ServerHolder(druidServer4, peon)
                                                                   )
                                                               )
                                        )
                                    )
                                )
                                .withLoadManagementPeons(ImmutableMap.of("1", peon, "2", peon, "3", peon, "4", peon))
                                .withAvailableSegments(segments.values())
                                .withMaxSegmentsToMove(MAX_SEGMENTS_TO_MOVE)
                                .build();

    params = new DruidMasterBalancer(master, new BalancerCostAnalyzer(new DateTime("2013-01-01"))).run(params);
    Assert.assertTrue(params.getMasterStats().getPerTierStats().get("movedCount").get("normal").get() > 0);
    Assert.assertTrue(params.getMasterStats().getPerTierStats().get("costChange").get("normal").get() > 0);
  }
}
