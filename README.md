## Flink开发目录结构
 - com.zhaopin
     * common 主要存放一些公共的一些连接信息，地址，表
     - flink
	 	* entity 主要存放一些实体类
	 	* job 存放flink的流作业
			 - dashboard  看板相关的任务
			 - feature 特征相关的任务
			 - metrics 宽表相关的任务
			 - push push相关的任务
			 - sdf 数据入库的任务
	 * sink Flink的输出源
	 * source Flink的输入源
	 * utils 存放工具类
