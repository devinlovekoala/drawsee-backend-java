# 教学向 Agentic RAG v2 架构设计文档

**项目定位**：高校电子电路教学智能问答系统  
**核心目标**：在教学场景下实现 *高可靠度处理频道切换 + 公式/形式化语言零容错 + 知识问答高度贴近教材*  
**设计原则**：教学优先、确定性优先、工程可扩展

---

## 1. 设计背景与升级动机

Phase 1 / Phase 2 Agentic RAG 架构已经解决了“是否使用 Agent”“是否引入工具”的问题，但在高校教学实际使用中暴露出三个关键不足：

1. 学生输入形式高度多样（自然语言 / 公式 / 拍照 / 形式语言），但系统未显式建模
2. 公式计算与电路形式语言分析仍可能被 LLM 自由发挥，存在低概率但不可接受的错误
3. ANALYSIS / DEBUG Agent 承担职责过宽，导致路由稳定性不足

因此，本 v2 架构的核心升级不是增加复杂度，而是**明确处理频道（Processing Channel）并强制约束不同类型问题的处理方式**。

---

## 2. 总体架构概览

```
User Input
   ↓
Input-Type Classifier   （输入载体识别）
   ↓
Intent Classifier       （教学意图识别）
   ↓
Adaptive Channel Router （处理频道路由）
   ↓
Processing Channel      （确定性处理）
   ↓
Answer Composer         （答案组织与教学表达）
```

---

## 3. Level-1：输入载体分类（Input-Type Classifier）

### 3.1 分类目标

回答的可靠性首先取决于**是否正确理解学生“给了什么”**，而不仅是“问了什么”。

### 3.2 输入载体分类标签

| INPUT_TYPE | 描述 | 示例 |
|-----------|------|------|
| NATURAL_QA | 纯自然语言问题 | “什么是KCL？” |
| FORMULA_PROBLEM | 明确的公式 / 数值计算问题 | “R1=10k,R2=20k 算分压” |
| CIRCUIT_NETLIST | 电路形式化语言 | SPICE netlist |
| CIRCUIT_DIAGRAM | 电路图 / 手写题 / 拍照 | 上传图片 |
| MIXED | 多模态混合输入 | “分析这个电路（附图）” |

### 3.3 实现建议

- LLM Few-shot + 规则增强（正则 / 关键词）
- 输出 JSON：

```json
{
  "input_type": "FORMULA_PROBLEM",
  "confidence": 0.98,
  "reason": "包含明确参数与计算动词"
}
```

---

## 4. Level-2：教学意图分类（Intent Classifier v2）

### 4.1 意图标签

| INTENT | 教学含义 |
|-------|----------|
| CONCEPT | 概念 / 原理理解 |
| RULE | 实验要求 / 作业规则 |
| COMPUTATION | 明确数值或公式计算 |
| ANALYSIS | 电路机理分析 |
| DEBUG | 故障原因排查 |

### 4.2 设计约束

- COMPUTATION 不得被归为 ANALYSIS
- 若 INPUT_TYPE = FORMULA_PROBLEM，默认优先 COMPUTATION

---

## 5. 处理频道（Processing Channel）设计

### Channel 1：Knowledge Channel（知识问答）

**触发条件**：
```
INPUT_TYPE = NATURAL_QA
INTENT = CONCEPT | RULE
```

**处理策略**：
- 强制 RAG（教材 / 实验文档 / 课件）
- 禁止脱离知识库自由发挥
- 输出需标注来源

---

### Channel 2：Formula Computation Channel（公式计算）

**触发条件**：
```
INPUT_TYPE = FORMULA_PROBLEM
INTENT = COMPUTATION
```

**核心原则**：
> LLM 不参与计算，只参与讲解

**工具链**：
- SymPy（解析、求解）
- Units 校验（防单位错误）

**输出要求**：
- 列出已知条件
- 给出使用公式
- 展示计算过程

---

### Channel 3：Circuit Formal Language Channel（形式语言）

**触发条件**：
```
INPUT_TYPE = CIRCUIT_NETLIST
```

**处理策略**：
- 语法检查
- 结构解释（节点、元件、连接关系）
- 教学级语义说明

---

### Channel 4：Circuit Diagram Channel（图片 / 拍照）

**触发条件**：
```
INPUT_TYPE = CIRCUIT_DIAGRAM | MIXED
```

**处理策略**：
- Vision Tool → 结构化描述
- 输出中间结构（用于教学）
- 再路由至 Channel 2 / 3 / 5

---

### Channel 5：Circuit Reasoning Channel（电路分析 / 调试）

**触发条件**：
```
INTENT = ANALYSIS | DEBUG
且输入已结构化
```

**处理策略**：
- 可用 RAG（相似电路）
- 可用 SQL（BOM / 参数）
- 可用受限计算工具

---

## 6. Router 决策逻辑（伪代码）

```python
if input_type == FORMULA_PROBLEM:
    route_to(FormulaChannel)
elif input_type == CIRCUIT_NETLIST:
    route_to(NetlistChannel)
elif input_type in [CIRCUIT_DIAGRAM, MIXED]:
    route_to(VisionChannel)
elif intent in [CONCEPT, RULE]:
    route_to(KnowledgeChannel)
else:
    route_to(CircuitReasoningChannel)
```

---

## 7. 教学价值与预期效果

| 维度 | 提升效果 |
|----|----|
| 公式计算正确率 | 接近 100% |
| 电路语言理解 | 确定性解析 |
| 学生信任度 | 显著提升 |
| 教师接受度 | 高 |
| 系统扩展性 | 强 |

---

## 8. 后续可演进方向（非当前必须）

- 引入 SPICE / 仿真工具
- 引入结果验证层
- 面向研究生的高阶分析频道

---

**文档版本**：Teaching-Oriented Agentic RAG v2  
**状态**：✅ 架构定稿，可直接进入实现阶段

