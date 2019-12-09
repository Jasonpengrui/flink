## Flink开发目录结构
### 代码部分 java
 * com.zhaopin
     * common 主要存放一些公共的一些连接信息，地址，表
     * flink
	 	* entity 主要存放一些实体类
	 	* job 存放flink的流作业
			 * dashboard  实时看板相关的任务
			 * feature 特征相关的任务
			 * metrics 宽表指标相关的任务
			 * push push相关的任务
			 * sdf 数据入库的任务
	 	* sink Flink的输出源 
	 	* source Flink的输入源 
	 * utils 存放工具类（包含Hbase、Http请求、时间转换等相关）
### 配置文件部分 resource
 * 将topic的信息写到topic.properties或者pulsar-info.yml
 * 写入druid的配置文件放到该目录下
 
**建议:**  
	* 1. 如果有一个项目包含很多个作业的时候，可以单独建一个package，把相关任务放到里面。	<br>	
	* 2. 把每个过程处理放到一个单独的class里面，这样更好把握整体的逻辑，流程清晰明了。	<br>	
	* 3. 可以将自己的作业提交命令放到resource资源目录下flink-start-command.yml里面，便于作业挂掉的时候，别的同事帮忙处理。 		
