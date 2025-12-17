# Agentic RAG v2 - Priority 3 实现指南

**开发阶段**: Priority 3 - 扩展专业频道
**日期**: 2025-12-16
**状态**: 设计完成，待实现

---

## 概述

Priority 3的目标是实现三个专业处理频道，完善Agentic RAG v2系统的能力矩阵。基于现有的**ngspice**和**verilog**仿真系统，我们可以快速集成强大的电路分析能力。

---

## 第1部分：NetlistChannel实现

### 1.1 设计目标

- **输入**: SPICE Netlist形式化语言
- **输出**: 结构化电路信息 + 教学性解释
- **集成**: 复用现有ngspice服务（localhost:3001）
- **特色**: 形式化解析 + 仿真验证 + 教学性说明

### 1.2 核心功能模块

#### 模块1: Netlist语法解析器

```python
def _parse_netlist_syntax(netlist: str) -> Dict:
    """
    SPICE语法解析

    解析规则:
    - 第一行: 标题
    - 元件行: <type><id> <nodes...> <value>
    - 控制行: .<command> [args]
    - 注释行: * text
    """
```

**支持的元件类型**:
- R/C/L: 无源元件（电阻/电容/电感）
- V/I: 电压源/电流源
- D: 二极管
- Q: 双极型晶体管（BJT）
- M: MOSFET
- J: JFET
- X: 子电路

**解析示例**:
```spice
RC Low-Pass Filter
V1 1 0 AC 1.0
R1 1 2 1k
C1 2 0 1u
.ac dec 10 1 100k
.end
```

解析结果:
```json
{
  "title": "RC Low-Pass Filter",
  "components": [
    {"id": "V1", "type": "voltage_source", "nodes": ["1", "0"], "value": "AC 1.0"},
    {"id": "R1", "type": "resistor", "nodes": ["1", "2"], "value": "1k"},
    {"id": "C1", "type": "capacitor", "nodes": ["2", "0"], "value": "1u"}
  ],
  "controls": [".ac dec 10 1 100k"]
}
```

#### 模块2: 拓扑分析器

```python
def _analyze_topology(structure: Dict) -> Dict:
    """
    电路拓扑分析

    提取:
    - 节点列表和连接性
    - 电源类型和位置
    - 无源/有源元件分类
    - 关键节点识别（输入/输出/地）
    """
```

**输出示例**:
```json
{
  "total_nodes": 3,
  "ground_node": "0",
  "voltage_sources": ["V1"],
  "passive_elements": ["R1", "C1"],
  "node_connectivity": {
    "1": ["V1", "R1"],
    "2": ["R1", "C1"],
    "0": ["V1", "C1"]
  }
}
```

#### 模块3: 问题检测器

```python
def _detect_issues(structure: Dict, topology: Dict) -> List[str]:
    """
    常见问题检测

    检查项:
    1. 缺少地节点（节点0）
    2. 缺少激励源
    3. 悬空节点（只连1个元件）
    4. 短路检测
    5. 元件参数异常
    """
```

**检测示例**:
```python
warnings = [
    "⚠️ 未找到地节点（节点0），电路可能无法仿真",
    "⚠️ 节点N5可能悬空（仅连接1个元件: R3）"
]
```

#### 模块4: Ngspice集成（可选）

```python
async def _run_simulation(netlist: str, context: Dict) -> Dict:
    """
    调用ngspice服务进行仿真

    集成点: http://localhost:3001/simulate

    请求:
    {
      "netlist": "完整SPICE网表",
      "analysisType": "ac|dc|tran"
    }

    响应:
    {
      "waveforms": {"v(2)": {"real": [...], "imag": [...]}},
      "statistics": {"min": ..., "max": ..., "avg": ...}
    }
    """
    import httpx

    async with httpx.AsyncClient() as client:
        response = await client.post(
            "http://localhost:3001/simulate",
            json={"netlist": netlist, "analysisType": "dc"}
        )
        return response.json()
```

### 1.3 完整代码

**文件路径**: `/home/devin/Workspace/python/drawsee-rag-python/app/services/agentic/netlist_channel.py`

请参考附录A中的完整实现代码（由于文件系统限制，代码已在前面提供）。

### 1.4 测试用例

```python
# 测试1: 简单RC滤波器
netlist = """
RC Low-Pass Filter
V1 1 0 AC 1.0
R1 1 2 1k
C1 2 0 1u
.ac dec 10 1 100k
.end
"""

result = await netlist_channel.process(netlist)
assert result["success"] == True
assert len(result["parsed_structure"]["nodes"]) == 3
assert "RC滤波" in result["explanation"]

# 测试2: 错误检测
bad_netlist = """
Test Circuit
R1 1 2 1k
* 缺少电源和地节点
.end
"""

result = await netlist_channel.process(bad_netlist)
assert len(result["warnings"]) > 0
```

---

## 第2部分：VisionChannel实现

### 2.1 设计目标

- **输入**: 电路图图片URL
- **输出**: 电路识别结果 + 再路由决策
- **集成**: GLM-4V视觉模型
- **特色**: 多模态理解 + 智能再路由

### 2.2 处理流程

```
图片输入
   ↓
GLM-4V识别
   ↓
结构化输出（元件+连接）
   ↓
判断是否需要再路由
   ├─ 是: 转换为query → AdaptiveRouter
   └─ 否: 直接返回识别结果
```

### 2.3 核心实现

```python
class VisionChannel:
    """
    电路图识别频道
    """

    def __init__(self):
        self.vision_client = AsyncOpenAI(
            api_key=settings.GLM_API_KEY,
            base_url="https://open.bigmodel.cn/api/paas/v4/"
        )
        self.vision_model = "glm-4v"

    async def process(
        self,
        query: str,
        image_url: str,
        knowledge_base_ids: List[str],
        context: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """
        处理电路图输入

        流程:
        1. GLM-4V识别电路元件和连接
        2. 解析识别结果
        3. 判断用户意图:
           - 仅识别: 返回结构化结果
           - 需要分析: 再路由到其他频道
        """
        try:
            # 第1步: 调用GLM-4V
            recognition_result = await self._recognize_circuit(image_url, query)

            if not recognition_result["success"]:
                return {
                    "success": False,
                    "error": "电路图识别失败",
                    "detail": recognition_result.get("error")
                }

            # 第2步: 解析识别结果
            circuit_data = self._parse_recognition_result(
                recognition_result["description"]
            )

            # 第3步: 判断是否需要再路由
            reroute_decision = self._should_reroute(query, circuit_data)

            if reroute_decision["reroute"]:
                # 再路由到其他频道
                logger.info(f"[VisionChannel] 再路由到 {reroute_decision['target_channel']}")

                # 导入路由器（避免循环依赖）
                from app.services.agentic.adaptive_router import adaptive_router

                # 转换为新的查询
                new_query = self._convert_to_query(query, circuit_data)

                # 调用路由器
                reroute_result = await adaptive_router.route(
                    query=new_query,
                    knowledge_base_ids=knowledge_base_ids,
                    has_image=False,  # 已识别，不再需要图片
                    context={
                        "from_vision": True,
                        "circuit_data": circuit_data
                    }
                )

                return {
                    "success": True,
                    "recognition": circuit_data,
                    "rerouted": True,
                    "reroute_channel": reroute_decision["target_channel"],
                    "reroute_result": reroute_result
                }

            else:
                # 仅返回识别结果
                return {
                    "success": True,
                    "recognition": circuit_data,
                    "rerouted": False,
                    "explanation": self._generate_circuit_description(circuit_data)
                }

        except Exception as e:
            logger.error(f"[VisionChannel] 处理失败: {e}", exc_info=True)
            return {
                "success": False,
                "error": "电路图处理失败",
                "detail": str(e)
            }

    async def _recognize_circuit(
        self,
        image_url: str,
        user_query: str
    ) -> Dict[str, Any]:
        """
        调用GLM-4V识别电路
        """
        prompt = f"""请识别这个电路图，提取以下信息：

1. **元件列表**：
   - 每个元件的类型（电阻/电容/电源等）
   - 元件标识（如R1, C1, V1）
   - 参数值（如1kΩ, 10μF, 5V）

2. **连接关系**：
   - 哪些元件相连
   - 节点连接情况

3. **电路类型判断**：
   - 这是什么类型的电路？（如RC滤波器、放大器等）

请用结构化格式输出，便于解析。

用户问题: {user_query}"""

        try:
            response = await self.vision_client.chat.completions.create(
                model=self.vision_model,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {"type": "image_url", "image_url": {"url": image_url}}
                        ]
                    }
                ],
                temperature=0.3
            )

            description = response.choices[0].message.content

            return {
                "success": True,
                "description": description
            }

        except Exception as e:
            logger.error(f"[VisionChannel] GLM-4V调用失败: {e}")
            return {
                "success": False,
                "error": str(e)
            }

    def _parse_recognition_result(self, description: str) -> Dict[str, Any]:
        """
        解析GLM-4V的识别结果

        提取:
        - 元件列表
        - 连接关系
        - 电路类型
        """
        # 简单的正则解析（实际应使用更robust的解析）
        components = []
        connections = []
        circuit_type = "未知"

        # 解析元件 (示例格式: R1: 1kΩ电阻)
        import re
        comp_pattern = r'([RCLDVIQMX]\d+):\s*(.+)'
        for match in re.finditer(comp_pattern, description):
            components.append({
                "id": match.group(1),
                "description": match.group(2)
            })

        # 解析连接 (示例格式: R1连接C1)
        conn_pattern = r'([RCLDVIQMX]\d+)连接([RCLDVIQMX]\d+)'
        for match in re.finditer(conn_pattern, description):
            connections.append({
                "from": match.group(1),
                "to": match.group(2)
            })

        # 提取电路类型
        if "滤波" in description:
            circuit_type = "滤波器"
        elif "放大" in description:
            circuit_type = "放大器"
        elif "振荡" in description:
            circuit_type = "振荡器"

        return {
            "components": components,
            "connections": connections,
            "circuit_type": circuit_type,
            "raw_description": description
        }

    def _should_reroute(
        self,
        query: str,
        circuit_data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        判断是否需要再路由

        规则:
        1. 用户问"是什么电路" → 不需要，直接返回识别
        2. 用户问"分析原理" → 再路由到ReasoningChannel
        3. 用户问"计算某参数" → 再路由到FormulaChannel
        """
        query_lower = query.lower()

        # 关键词匹配
        analysis_keywords = ["分析", "原理", "为什么", "如何工作"]
        computation_keywords = ["计算", "求", "多少"]

        if any(kw in query_lower for kw in analysis_keywords):
            return {
                "reroute": True,
                "target_channel": "REASONING",
                "reason": "用户要求分析电路原理"
            }

        if any(kw in query_lower for kw in computation_keywords):
            return {
                "reroute": True,
                "target_channel": "FORMULA",
                "reason": "用户要求计算参数"
            }

        # 默认不再路由
        return {
            "reroute": False,
            "reason": "仅识别电路，无需进一步处理"
        }

    def _convert_to_query(
        self,
        original_query: str,
        circuit_data: Dict[str, Any]
    ) -> str:
        """
        将图片+问题转换为纯文本查询
        """
        # 构建电路描述
        circuit_desc = f"电路类型: {circuit_data['circuit_type']}\n"
        circuit_desc += "元件列表:\n"
        for comp in circuit_data["components"]:
            circuit_desc += f"- {comp['id']}: {comp['description']}\n"

        # 合并原始问题
        new_query = f"{circuit_desc}\n用户问题: {original_query}"

        return new_query
```

### 2.4 测试用例

```python
# 测试1: 纯识别
result = await vision_channel.process(
    query="这是什么电路？",
    image_url="https://example.com/circuit.png",
    knowledge_base_ids=["kb_001"]
)

assert result["success"] == True
assert result["rerouted"] == False
assert len(result["recognition"]["components"]) > 0

# 测试2: 识别+分析再路由
result = await vision_channel.process(
    query="分析这个电路的工作原理",
    image_url="https://example.com/circuit.png",
    knowledge_base_ids=["kb_001"]
)

assert result["success"] == True
assert result["rerouted"] == True
assert result["reroute_channel"] == "REASONING"
```

---

## 第3部分：CircuitReasoningChannel实现

### 3.1 设计目标

- **输入**: 复杂电路分析/调试问题
- **输出**: 深度推理结果 + 工具链执行记录
- **集成**: RAG + SQL + 受限计算 + Agent
- **特色**: 多步骤推理 + 工具调用

### 3.2 Agent架构

```
用户问题
   ↓
IntentClassifier: ANALYSIS/DEBUG
   ↓
CircuitReasoningChannel
   ├─ 工具1: HybridSearch (RAG知识检索)
   ├─ 工具2: Calculator (受限数值计算)
   ├─ 工具3: SQLQuery (结构化数据查询)
   └─ 工具4: CircuitSimulator (调用ngspice/verilog)
   ↓
LLM决策: 选择工具序列
   ↓
执行工具链
   ↓
聚合结果 → 生成答案
```

### 3.3 核心实现

```python
class CircuitReasoningChannel:
    """
    电路推理频道

    使用Agent模式处理复杂分析和调试任务
    """

    def __init__(self):
        self.client = AsyncOpenAI(
            api_key=settings.LLM_API_KEY,
            base_url=settings.LLM_BASE_URL,
        )
        self.model = settings.LLM_MODEL

        # 工具注册
        self.tools = [
            HybridSearchTool(),       # RAG检索
            CalculatorTool(),         # 数值计算
            SQLQueryTool(),           # 数据库查询
            CircuitSimulatorTool()    # 仿真工具
        ]

    async def process(
        self,
        query: str,
        knowledge_base_ids: List[str],
        intent: str,
        context: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """
        Agent推理处理

        流程:
        1. 分析问题，规划工具序列
        2. 迭代调用工具
        3. 综合结果生成答案
        """
        try:
            logger.info(f"[ReasoningChannel] 开始推理: query='{query[:50]}...', intent={intent}")

            # 第1步: 规划
            plan = await self._plan_reasoning(query, intent)

            # 第2步: 执行工具链
            tool_results = []
            for step in plan["steps"]:
                tool_name = step["tool"]
                tool_args = step["args"]

                logger.info(f"[ReasoningChannel] 执行工具: {tool_name}")

                tool = self._get_tool(tool_name)
                result = await tool.execute(tool_args)

                tool_results.append({
                    "tool": tool_name,
                    "args": tool_args,
                    "result": result
                })

            # 第3步: 综合生成答案
            answer = await self._synthesize_answer(query, tool_results)

            return {
                "success": True,
                "answer": answer,
                "tool_calls": tool_results,
                "confidence": 0.9,
                "intent": intent
            }

        except Exception as e:
            logger.error(f"[ReasoningChannel] 推理失败: {e}", exc_info=True)
            return {
                "success": False,
                "error": "电路推理失败",
                "detail": str(e)
            }

    async def _plan_reasoning(
        self,
        query: str,
        intent: str
    ) -> Dict[str, Any]:
        """
        规划推理步骤

        使用Function Calling让LLM选择工具序列
        """
        # 工具schema
        tools_schema = [tool.get_schema() for tool in self.tools]

        system_prompt = """你是一个电路分析专家Agent，可以使用以下工具：
1. hybrid_search: 在知识库中检索相关电路知识
2. calculator: 进行数值计算（加减乘除、幂运算等）
3. sql_query: 查询电路数据库
4. circuit_simulator: 运行电路仿真

请根据用户问题，规划需要使用的工具序列。"""

        user_prompt = f"""用户问题: {query}
意图类型: {intent}

请规划解决这个问题需要的工具调用步骤。"""

        response = await self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            tools=tools_schema,
            tool_choice="auto"
        )

        # 解析Function Calling结果
        tool_calls = response.choices[0].message.tool_calls or []

        steps = []
        for tool_call in tool_calls:
            import json
            steps.append({
                "tool": tool_call.function.name,
                "args": json.loads(tool_call.function.arguments)
            })

        return {"steps": steps}

    def _get_tool(self, tool_name: str):
        """获取工具实例"""
        for tool in self.tools:
            if tool.name == tool_name:
                return tool
        raise ValueError(f"未找到工具: {tool_name}")

    async def _synthesize_answer(
        self,
        query: str,
        tool_results: List[Dict[str, Any]]
    ) -> str:
        """
        综合工具结果生成答案
        """
        # 构建上下文
        context_parts = [f"用户问题: {query}", "\n工具执行结果:"]

        for i, result in enumerate(tool_results, 1):
            context_parts.append(f"\n{i}. {result['tool']}:")
            context_parts.append(f"   参数: {result['args']}")
            context_parts.append(f"   结果: {result['result']}")

        context_text = "\n".join(context_parts)

        # 生成答案
        system_prompt = """你是电路分析助教，根据工具执行结果回答用户问题。

要求:
1. 综合所有工具结果
2. 使用清晰的教学语言
3. 给出完整的推理过程
4. 必要时提供公式和数据"""

        user_prompt = f"""{context_text}

请基于上述工具结果，回答用户的问题。"""

        response = await self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=0.3,
            max_tokens=1000
        )

        answer = response.choices[0].message.content.strip()
        return answer
```

### 3.4 工具实现示例

```python
class CircuitSimulatorTool(BaseTool):
    """电路仿真工具"""

    name = "circuit_simulator"
    description = "运行SPICE/Verilog电路仿真"

    def get_schema(self) -> Dict:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": {
                        "netlist": {
                            "type": "string",
                            "description": "SPICE网表内容"
                        },
                        "analysis_type": {
                            "type": "string",
                            "enum": ["dc", "ac", "tran"],
                            "description": "仿真类型"
                        }
                    },
                    "required": ["netlist", "analysis_type"]
                }
            }
        }

    async def execute(self, args: Dict) -> Dict:
        """调用ngspice服务"""
        import httpx

        async with httpx.AsyncClient() as client:
            response = await client.post(
                "http://localhost:3001/simulate",
                json={
                    "netlist": args["netlist"],
                    "analysisType": args["analysis_type"]
                },
                timeout=30.0
            )

            return response.json()
```

---

## 第4部分：集成到AdaptiveRouter

### 4.1 更新路由器

**文件**: `/home/devin/Workspace/python/drawsee-rag-python/app/services/agentic/adaptive_router.py`

```python
from app.services.agentic.netlist_channel import netlist_channel
from app.services.agentic.vision_channel import vision_channel
from app.services.agentic.reasoning_channel import reasoning_channel

class AdaptiveChannelRouter:
    def __init__(self):
        # ... 现有代码 ...

        # 添加新频道
        self.channels = {
            ChannelType.KNOWLEDGE: knowledge_channel,
            ChannelType.FORMULA: formula_channel,
            ChannelType.NETLIST: netlist_channel,      # 新增
            ChannelType.VISION: vision_channel,        # 新增
            ChannelType.REASONING: reasoning_channel,  # 新增
        }
```

### 4.2 测试完整路由

```python
# 测试1: Netlist路由
result = await adaptive_router.route(
    query="R1 1 0 1k\nV1 1 0 DC 5",
    knowledge_base_ids=["kb_001"]
)
assert result["channel"] == "NETLIST"

# 测试2: Vision路由
result = await adaptive_router.route(
    query="分析这个电路",
    knowledge_base_ids=["kb_001"],
    has_image=True,
    image_url="https://example.com/circuit.png"
)
assert result["channel"] == "VISION"

# 测试3: Reasoning路由
result = await adaptive_router.route(
    query="为什么这个放大电路的增益不够？",
    knowledge_base_ids=["kb_001"]
)
assert result["channel"] == "REASONING"
```

---

## 第5部分：部署和测试

### 5.1 启动ngspice服务

```bash
cd /home/devin/Workspace/drawsee-platform/drawsee-web
docker-compose up ngspice-backend
```

### 5.2 启动Python RAG服务

```bash
cd /home/devin/Workspace/python/drawsee-rag-python
python -m uvicorn app.main:app --port 8000
```

### 5.3 端到端测试

```bash
# 测试NetlistChannel
curl -X POST http://localhost:8000/api/v1/rag/agentic/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "R1 1 2 1k\nC1 2 0 1u\nV1 1 0 DC 5\n.end",
    "knowledge_base_ids": ["kb_001"]
  }'
```

---

## 第6部分：性能优化

### 6.1 Netlist解析缓存

```python
from functools import lru_cache

@lru_cache(maxsize=100)
def _parse_netlist_cached(netlist_hash: str) -> Dict:
    """缓存解析结果"""
    pass
```

### 6.2 GLM-4V调用限流

```python
from aiohttp_limiter import Limiter

limiter = Limiter(rate=10, time_period=60)  # 10次/分钟

@limiter.limit()
async def _recognize_circuit(self, image_url: str):
    # ... GLM-4V调用
```

### 6.3 仿真任务异步化

```python
from celery import Celery

celery_app = Celery('tasks', broker='redis://localhost:6379')

@celery_app.task
def run_ngspice_simulation(netlist: str):
    """后台运行仿真"""
    # ... 调用ngspice
```

---

## 附录：完整文件结构

```
app/services/agentic/
├── __init__.py
├── input_type_classifier.py      (Priority 1 ✅)
├── intent_classifier.py           (Priority 1 ✅)
├── formula_channel.py             (Priority 1 ✅)
├── knowledge_channel.py           (Priority 1 ✅)
├── adaptive_router.py             (Priority 1 ✅)
├── netlist_channel.py             (Priority 3 ⏳ 本次实现)
├── vision_channel.py              (Priority 3 ⏳ 本次实现)
├── reasoning_channel.py           (Priority 3 ⏳ 本次实现)
└── tools.py                       (Priority 3 ⏳ 工具链)
```

---

**实现指南版本**: 1.0
**状态**: 设计完成，代码模板已提供
**预计实现时间**: 2-3天
**下一步**: 按照本文档逐步实现三个频道

