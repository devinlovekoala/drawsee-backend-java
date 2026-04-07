package cn.yifan.drawsee;

import cn.yifan.drawsee.pojo.rabbit.AnimationTaskMessage;
import cn.yifan.drawsee.service.base.AiService;
import cn.yifan.drawsee.service.base.ApiService;
import cn.yifan.drawsee.service.base.PromptService;
import cn.yifan.drawsee.service.base.StreamAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@SuppressWarnings("unused")
class DrawseeApplicationTests {
  @Autowired private AiService aiService;
  @Autowired private StreamAiService streamAiService;
  @Autowired private RedissonClient redissonClient;

  /*@Test
  void testAiService() {
      List<String> knowledgePoints = knowledgeRepository.findAll().stream().map((Knowledge::getName)).toList();
      String question = "行列式是什么啊？";
      String title = aiService.getConvTitle(question);
      System.out.println(title);
      List<String> relatedKnowledgePoints = aiService.getRelatedKnowledgePoints(knowledgePoints, question);
      System.out.println(relatedKnowledgePoints);
  }

  @Test
  void layoutTest() {
      // 创建根节点
      ElkNode root = ElkGraphUtil.createGraph();

      // 创建两个子节点
      ElkNode node1 = ElkGraphUtil.createNode(root);
      node1.setWidth(50);
      node1.setHeight(50);
      node1.setX(200);
      node1.setY(400);
      node1.setProperty(CoreOptions.NODE_SIZE_CONSTRAINTS, SizeConstraint.minimumSize());
      node1.setProperty(CoreOptions.NODE_SIZE_MINIMUM, new KVector(50, 50));
      node1.setProperty(MrTreeMetaDataProvider.TREE_LEVEL, 0);
      node1.setProperty(MrTreeMetaDataProvider.POSITION_CONSTRAINT, 1);


      ElkNode node2 = ElkGraphUtil.createNode(root);
      node2.setWidth(50);
      node2.setHeight(50);
      node2.setX(100);
      node2.setY(500);
      node2.setProperty(CoreOptions.NODE_SIZE_CONSTRAINTS, SizeConstraint.minimumSize());
      node2.setProperty(CoreOptions.NODE_SIZE_MINIMUM, new KVector(50, 50));
      node2.setProperty(MrTreeMetaDataProvider.TREE_LEVEL, 1);
      node2.setProperty(MrTreeMetaDataProvider.POSITION_CONSTRAINT, 1);

      // 创建边
      ElkEdge edge = ElkGraphUtil.createEdge(root);
      edge.getSources().add(node1);
      edge.getTargets().add(node2);

      // 设置布局选项
      root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.mrtree"); // 设置算法
      root.setProperty(CoreOptions.DIRECTION, Direction.DOWN); // 方向向下
      root.setProperty(MrTreeMetaDataProvider.EDGE_ROUTING_MODE, EdgeRoutingMode.AVOID_OVERLAP); // 边避免重叠
      root.setProperty(CoreOptions.INTERACTIVE, true); // 交互模式
      root.setProperty(CoreOptions.INTERACTIVE_LAYOUT, true); // 交互模式
      root.setProperty(CoreOptions.SPACING_NODE_NODE, 40D); // 节点间距
      root.setProperty(MrTreeMetaDataProvider.WEIGHTING, OrderWeighting.CONSTRAINT); // 权重限制

      // 创建布局引擎
      RecursiveGraphLayoutEngine layoutEngine = new RecursiveGraphLayoutEngine();
      BasicProgressMonitor monitor = new BasicProgressMonitor();
      layoutEngine.layout(root, monitor);
      // 输出布局结果
      for (ElkNode node : root.getChildren()) {
          System.out.println("Node position: (" + node.getX() + ", " + node.getY() + ")");
      }
      String json = ElkGraphJson.forGraph(root)
              .shortLayoutOptionKeys(false)
              .prettyPrint(true)
              .toJson();
      System.out.println(json);
  }*/

  /*@Test
  void relatedKnowledge() {
      // 获取相关知识点
      RList<String> rList = redissonClient.getList(RedisKeyPrefix.CACHE_PREFIX + "knowledge-points");
      List<String> knowledgePoints = rList.stream().toList();
      List<String> relatedKnowledgePoints = aiService.getRelatedKnowledgePoints(knowledgePoints, "分块矩阵怎么相乘呢？");
      for (String knowledgePoint : relatedKnowledgePoints) {
          System.out.println(knowledgePoint);
      }
  }*/

  // @Autowired private ChatLanguageModel deepseekV3ChatLanguageModel;
  @Autowired private PromptService promptService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ApiService apiService;
  @Autowired private RabbitTemplate rabbitTemplate;

  /*@Test
  void langchainTest() throws JsonProcessingException, ExecutionException, InterruptedException {
      String question = "线性变化从几何上如何理解啊？";
      String animationShotTextListPrompt = promptService.getAnimationShotTextListPrompt(question);
      String animationShotTextListResult = deepseekV3ChatLanguageModel.chat(animationShotTextListPrompt);
      TypeReference<List<Map<String, String>>> typeReference = new TypeReference<>() {};
      List<Map<String, String>> animationShotTextList = objectMapper.readValue(animationShotTextListResult, typeReference);

      Map<Integer, Map<String, String>> animationShotInfoMap = new ConcurrentHashMap<>();

      // 创建CompletableFuture列表
      List<CompletableFuture<Void>> futures = new ArrayList<>();

      for (int i = 0; i < animationShotTextList.size(); i++) {
          Map<String, String> animationShotText = animationShotTextList.get(i);
          String shotDescription = animationShotText.get("shotDescription");
          String shotScript = animationShotText.get("shotScript");
          final int index = i + 1; // 创建final变量用于lambda表达式

          // 为每个镜头创建异步任务
          CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
              String animationShotCodePrompt = promptService.getAnimationShotCodePrompt(shotDescription, shotScript);
              String animationShotCodeResult = deepseekV3ChatLanguageModel.chat(animationShotCodePrompt);
              Map<String, String> animationShotInfo = new ConcurrentHashMap<>();
              animationShotInfo.put("镜头描述：", shotDescription);
              animationShotInfo.put("镜头脚本：", shotScript);
              animationShotInfo.put("manim代码：", animationShotCodeResult);
              animationShotInfoMap.put(index, animationShotInfo);
              System.out.println("------------------------第" + index + "个镜头-------------------------");
              System.out.println("镜头描述：" + shotDescription);
              System.out.println("镜头脚本：" + shotScript);
              System.out.println("manim代码：" + animationShotCodeResult);
          });
          futures.add(future);
      }

      // 等待所有异步任务完成
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

      // 根据animationShotInfoMap的key排序获取animationShotInfoList
      List<Map<String, String>> animationShotInfoList = animationShotInfoMap.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .map(Map.Entry::getValue)
          .toList();

      String animationShotMergeCodePrompt = promptService.getAnimationShotMergeCodePrompt(animationShotInfoList.toString());
      String animationShotMergeCodeResult = deepseekV3ChatLanguageModel.chat(animationShotMergeCodePrompt);
      System.out.println("------------------------最终结果-------------------------");
      System.out.println(animationShotMergeCodeResult);
  }

  @Test
  void apiTest() {
      //apiService.renderAnimation(10L, "asasdafadsd");
  }*/

  @Test
  void test() {
    // 创建AnimationTaskMessage
    AnimationTaskMessage animationTaskMessage =
        new AnimationTaskMessage(
            234L,
            651L,
            """

        from manim import *

        class ManimScene(Scene):
            def construct(self):
                # 镜头1: 介绍矩阵乘法
                intro_text = Tex("同学们好，今天我们来深入了解矩阵相乘。\\\\\\\\现在呈现在大家眼前的，就是我们即将进行\\\\\\\\乘法运算的两个矩阵，矩阵A是2行3列，\\\\\\\\矩阵B是3行2列。在数学中，我们常常需要\\\\\\\\处理大量的数据关系，矩阵乘法就是一种\\\\\\\\非常重要的运算方式。").scale(0.6)
                self.play(Write(intro_text))
                self.wait(3)
                self.play(FadeOut(intro_text))

                # 创建矩阵A和矩阵B
                matrix_A = Matrix([[1, 2, 3], [4, 5, 6]], v_buff=0.7, h_buff=1.2).set_color(YELLOW)
                matrix_B = Matrix([[7, 8], [9, 10], [11, 12]], v_buff=0.7, h_buff=1.2).set_color(GREEN)

                # 将矩阵A和矩阵B移动到画面中央
                matrix_A.move_to(LEFT * 3)
                matrix_B.move_to(RIGHT * 3)

                # 展示矩阵A和矩阵B
                self.play(FadeIn(matrix_A), FadeIn(matrix_B))
                self.wait(2)

                # 镜头结束
                self.play(FadeOut(matrix_A), FadeOut(matrix_B))
                self.wait(1)

                # 镜头2: 矩阵A的第一行和矩阵B的第一列
                matrix_a = Matrix([[1, 2], [3, 4]]).to_edge(LEFT)
                matrix_b = Matrix([[5, 6], [7, 8]]).to_edge(RIGHT)
               \s
                # 添加矩阵标签
                label_a = Tex("矩阵A").next_to(matrix_a, UP)
                label_b = Tex("矩阵B").next_to(matrix_b, UP)
               \s
                # 展示矩阵和标签
                self.play(Write(matrix_a), Write(matrix_b), Write(label_a), Write(label_b))
                self.wait(1)
               \s
                # 获取矩阵A的第一行和矩阵B的第一列
                row_a = matrix_a.get_rows()[0]
                col_b = matrix_b.get_columns()[0]
               \s
                # 创建光线
                rays_a = [Arrow(row_a.get_left(), row_a.get_right(), color=YELLOW) for _ in row_a]
                rays_b = [Arrow(col_b.get_top(), col_b.get_bottom(), color=BLUE) for _ in col_b]
               \s
                # 展示光线
                self.play(*[Create(ray) for ray in rays_a], *[Create(ray) for ray in rays_b])
                self.wait(1)
               \s
                # 创建交汇点
                intersection_points = [Dot(color=RED).move_to(ray_a.get_end()) for ray_a in rays_a]
               \s
                # 展示交汇点
                self.play(*[Create(point) for point in intersection_points])
                self.wait(1)
               \s
                # 淡出光线和交汇点
                self.play(*[FadeOut(ray) for ray in rays_a], *[FadeOut(ray) for ray in rays_b], *[FadeOut(point) for point in intersection_points])
                self.wait(1)
               \s
                # 淡出矩阵和标签
                self.play(FadeOut(matrix_a), FadeOut(matrix_b), FadeOut(label_a), FadeOut(label_b))
                self.wait(1)

                # 镜头3: 矩阵A的第一行和矩阵B的第一列的元素相乘
                matrix_A = Matrix([[1, 2], [3, 4]])
                matrix_B = Matrix([[5, 6], [7, 8]])
               \s
                # 将矩阵A和B放置在场景中
                matrix_A.move_to(LEFT * 3)
                matrix_B.move_to(RIGHT * 3)
               \s
                # 显示矩阵A和B
                self.play(Write(matrix_A), Write(matrix_B))
                self.wait(1)
               \s
                # 获取矩阵A第一行第一个元素和矩阵B第一列第一个元素
                a11 = matrix_A.get_entries()[0]
                b11 = matrix_B.get_entries()[0]
               \s
                # 创建光线并加粗为红色
                line = Line(a11.get_right(), b11.get_left(), color=RED, stroke_width=8)
                self.play(Create(line))
                self.wait(1)
               \s
                # 计算乘积并显示
                product = Tex(f"${a11.get_tex_string()} \\\\times {b11.get_tex_string()} = {int(a11.get_tex_string()) * int(b11.get_tex_string())}$").next_to(line, UP)
                self.play(Write(product))
                self.wait(1)
               \s
                # 对其他对应元素进行乘法运算
                a12 = matrix_A.get_entries()[1]
                b21 = matrix_B.get_entries()[2]
                line2 = Line(a12.get_right(), b21.get_left(), color=RED, stroke_width=8)
                self.play(Create(line2))
                self.wait(1)
               \s
                product2 = Tex(f"${a12.get_tex_string()} \\\\times {b21.get_tex_string()} = {int(a12.get_tex_string()) * int(b21.get_tex_string())}$").next_to(line2, UP)
                self.play(Write(product2))
                self.wait(1)
               \s
                a21 = matrix_A.get_entries()[2]
                b12 = matrix_B.get_entries()[1]
                line3 = Line(a21.get_right(), b12.get_left(), color=RED, stroke_width=8)
                self.play(Create(line3))
                self.wait(1)
               \s
                product3 = Tex(f"${a21.get_tex_string()} \\\\times {b12.get_tex_string()} = {int(a21.get_tex_string()) * int(b12.get_tex_string())}$").next_to(line3, UP)
                self.play(Write(product3))
                self.wait(1)
               \s
                a22 = matrix_A.get_entries()[3]
                b22 = matrix_B.get_entries()[3]
                line4 = Line(a22.get_right(), b22.get_left(), color=RED, stroke_width=8)
                self.play(Create(line4))
                self.wait(1)
               \s
                product4 = Tex(f"${a22.get_tex_string()} \\\\times {b22.get_tex_string()} = {int(a22.get_tex_string()) * int(b22.get_tex_string())}$").next_to(line4, UP)
                self.play(Write(product4))
                self.wait(1)
               \s
                # 淡出所有元素
                self.play(FadeOut(matrix_A), FadeOut(matrix_B), FadeOut(line), FadeOut(product), FadeOut(line2), FadeOut(product2), FadeOut(line3), FadeOut(product3), FadeOut(line4), FadeOut(product4))
                self.wait(1)

                # 镜头4: 乘积数值汇聚到结果矩阵C的第一行第一列
                product_values = [2, 3, 4]
                product_texts = VGroup(*[Tex(f"${value}$") for value in product_values])
                product_texts.arrange(RIGHT, buff=1)
                product_texts.to_edge(UP)

                # 显示乘积数值
                self.play(Write(product_texts))
                self.wait(1)

                # 创建求和数值
                sum_value = sum(product_values)
                sum_text = Tex(f"${sum_value}$")
                sum_text.move_to(product_texts.get_center())

                # 乘积数值汇聚到求和数值
                self.play(
                    *[ReplacementTransform(product_texts[i], sum_text) for i in range(len(product_values))]
                )
                self.wait(1)

                # 创建结果矩阵C
                matrix_c = Matrix([[sum_value, 0], [0, 0]])
                matrix_c.to_edge(DOWN)

                # 显示结果矩阵C
                self.play(Write(matrix_c))
                self.wait(1)

                # 求和数值移动到结果矩阵C的第一行第一列
                self.play(sum_text.animate.move_to(matrix_c.get_entries()[0].get_center()))
                self.wait(1)

                # 淡出所有元素
                self.play(FadeOut(sum_text), FadeOut(matrix_c))
                self.wait(1)

                # 镜头5: 矩阵A的第一行和矩阵B的第二列
                matrix_a = Matrix([[1, 2], [3, 4]]).to_edge(UP).shift(LEFT * 3)
                matrix_b = Matrix([[5, 6], [7, 8]]).to_edge(UP).shift(RIGHT * 3)
                matrix_c = Matrix([[0, 0], [0, 0]]).to_edge(DOWN)

                # 添加矩阵标签
                label_a = Tex("$A$").next_to(matrix_a, LEFT)
                label_b = Tex("$B$").next_to(matrix_b, LEFT)
                label_c = Tex("$C$").next_to(matrix_c, LEFT)

                # 显示矩阵和标签
                self.play(Write(matrix_a), Write(matrix_b), Write(matrix_c))
                self.play(Write(label_a), Write(label_b), Write(label_c))
                self.wait(1)

                # 获取矩阵A的第一行和矩阵B的第二列
                row_a = matrix_a.get_rows()[0]
                col_b = matrix_b.get_columns()[1]

                # 创建光线
                ray_a = Arrow(row_a.get_right(), col_b.get_top(), color=YELLOW)
                ray_b = Arrow(row_a.get_right(), col_b.get_bottom(), color=YELLOW)

                # 显示光线
                self.play(Create(ray_a), Create(ray_b))
                self.wait(1)

                # 创建交汇点
                intersection = Dot(ray_a.get_end(), color=RED)

                # 显示交汇点
                self.play(Create(intersection))
                self.wait(1)

                # 计算矩阵C的第一行第二列元素
                result = row_a[0].get_tex_string() + " \\\\times " + col_b[0].get_tex_string() + " + " + row_a[1].get_tex_string() + " \\\\times " + col_b[1].get_tex_string()
                result_text = Tex(result).next_to(intersection, DOWN)

                # 显示计算结果
                self.play(Write(result_text))
                self.wait(1)

                # 移动结果到矩阵C的第一行第二列
                self.play(result_text.animate.move_to(matrix_c.get_entries()[1]))
                self.wait(1)

                # 更新矩阵C的第一行第二列元素
                matrix_c.get_entries()[1].set_tex_string(str(eval(result)))
                self.play(FadeOut(result_text))
                self.wait(1)

                # 淡出光线和交汇点
                self.play(FadeOut(ray_a), FadeOut(ray_b), FadeOut(intersection))
                self.wait(1)

                # 结束场景
                self.play(FadeOut(matrix_a), FadeOut(matrix_b), FadeOut(matrix_c), FadeOut(label_a), FadeOut(label_b), FadeOut(label_c))
                self.wait(1)

                # 镜头6: 矩阵A的第二行和矩阵B的第一列
                matrix_a = Matrix([[1, 2], [3, 4]]).to_edge(UP + LEFT)
                matrix_b = Matrix([[5, 6], [7, 8]]).to_edge(UP + RIGHT)
                matrix_c = Matrix([[0, 0], [0, 0]]).to_edge(DOWN)

                # 添加矩阵标签
                label_a = Tex("A").next_to(matrix_a, LEFT)
                label_b = Tex("B").next_to(matrix_b, LEFT)
                label_c = Tex("C").next_to(matrix_c, LEFT)

                # 展示矩阵和标签
                self.play(Write(matrix_a), Write(matrix_b), Write(matrix_c))
                self.play(Write(label_a), Write(label_b), Write(label_c))
                self.wait(1)

                # 高亮矩阵A的第二行和矩阵B的第一列
                a_row_highlight = SurroundingRectangle(matrix_a.get_rows()[1], color=YELLOW)
                b_col_highlight = SurroundingRectangle(matrix_b.get_columns()[0], color=YELLOW)

                self.play(Create(a_row_highlight), Create(b_col_highlight))
                self.wait(1)

                # 显示说明文字
                explanation = Tex("现在轮到矩阵A的第二行和矩阵B的第一列了。\\\\\\\\同样的方法，对应元素相乘再相加，\\\\\\\\得出结果矩阵C中第二行第一列的元素值。").scale(0.8).to_edge(DOWN)
                self.play(Write(explanation))
                self.wait(2)

                # 创建光线和数值移动动画
                a_row_elements = matrix_a.get_rows()[1]
                b_col_elements = matrix_b.get_columns()[0]

                for i in range(len(a_row_elements)):
                    # 创建光线
                    line = Line(a_row_elements[i].get_center(), b_col_elements[i].get_center(), color=BLUE)
                    self.play(Create(line))
                    self.wait(0.5)

                    # 创建数值移动动画
                    product = Tex(f"{a_row_elements[i].get_tex_string()} \\\\times {b_col_elements[i].get_tex_string()}").scale(0.8).move_to(line.get_center())
                    self.play(Write(product))
                    self.wait(0.5)

                    # 移动数值到结果矩阵C
                    self.play(product.animate.move_to(matrix_c.get_rows()[1][0].get_center()))
                    self.wait(0.5)

                    # 移除光线和数值
                    self.play(FadeOut(line), FadeOut(product))

                # 计算并显示结果
                result = Tex("3 \\\\times 5 + 4 \\\\times 7 = 15 + 28 = 43").scale(0.8).next_to(matrix_c, DOWN)
                self.play(Write(result))
                self.wait(1)

                # 更新结果矩阵C
                matrix_c.get_rows()[1][0].set_tex("43")
                self.play(Write(matrix_c.get_rows()[1][0]))
                self.wait(1)

                # 移除高亮和说明文字
                self.play(FadeOut(a_row_highlight), FadeOut(b_col_highlight), FadeOut(explanation), FadeOut(result))
                self.wait(1)

                # 结束
                self.play(FadeOut(matrix_a), FadeOut(matrix_b), FadeOut(matrix_c), FadeOut(label_a), FadeOut(label_b), FadeOut(label_c))
                self.wait(1)

                # 镜头7: 矩阵A的第二行和矩阵B的第二列
                matrix_A = Matrix([[1, 2], [3, 4]]).shift(LEFT * 3)
                matrix_B = Matrix([[5, 6], [7, 8]]).shift(RIGHT * 3)
                matrix_C = Matrix([[0, 0], [0, 0]]).shift(DOWN * 2)
               \s
                # 添加矩阵标签
                label_A = Tex("A").next_to(matrix_A, UP)
                label_B = Tex("B").next_to(matrix_B, UP)
                label_C = Tex("C").next_to(matrix_C, UP)
               \s
                # 展示矩阵A, B, C
                self.play(Write(matrix_A), Write(matrix_B), Write(matrix_C))
                self.play(Write(label_A), Write(label_B), Write(label_C))
                self.wait(1)
               \s
                # 高亮矩阵A的第二行和矩阵B的第二列
                highlight_A_row = SurroundingRectangle(matrix_A.get_rows()[1], color=YELLOW)
                highlight_B_col = SurroundingRectangle(matrix_B.get_columns()[1], color=YELLOW)
               \s
                self.play(Create(highlight_A_row), Create(highlight_B_col))
                self.wait(1)
               \s
                # 创建光线从矩阵A的第二行射向矩阵B的第二列
                start_points = [matrix_A.get_rows()[1][i].get_center() for i in range(2)]
                end_points = [matrix_B.get_columns()[1][i].get_center() for i in range(2)]
               \s
                lines = [Line(start_points[i], end_points[i], color=BLUE) for i in range(2)]
                self.play(*[Create(line) for line in lines])
                self.wait(1)
               \s
                # 计算矩阵C的最后一个元素
                result = matrix_A.get_rows()[1][0].get_tex_string() + " \\\\times " + matrix_B.get_columns()[1][0].get_tex_string() + " + " + matrix_A.get_rows()[1][1].get_tex_string() + " \\\\times " + matrix_B.get_columns()[1][1].get_tex_string()
                result_tex = Tex(result).shift(UP * 2)
               \s
                self.play(Write(result_tex))
                self.wait(1)
               \s
                # 显示计算结果
                final_result = str(3*7 + 4*8)
                final_result_tex = Tex(final_result).move_to(matrix_C.get_rows()[1][1].get_center())
               \s
                self.play(Transform(result_tex, final_result_tex))
                self.wait(1)
               \s
                # 更新矩阵C
                matrix_C_new = Matrix([[19, 22], [43, 50]]).shift(DOWN * 2)
                self.play(Transform(matrix_C, matrix_C_new))
                self.wait(1)
               \s
                # 清理场景
                self.play(FadeOut(highlight_A_row), FadeOut(highlight_B_col), FadeOut(result_tex), *[FadeOut(line) for line in lines])
                self.wait(1)

                # 镜头8: 矩阵旋转
                axes = Axes(
                    x_range=[-3, 3, 1],
                    y_range=[-3, 3, 1],
                    axis_config={"color": BLUE},
                )
                axes_labels = axes.get_axis_labels(x_label="x", y_label="y")

                # 创建三角形
                triangle = Polygon(
                    [1, 0, 0], [0, 1, 0], [-1, 0, 0], color=YELLOW
                )

                # 创建旋转矩阵
                rotation_matrix = MathTex(
                    r"\\begin{bmatrix} \\cos(\\theta) & -\\sin(\\theta) \\\\ \\sin(\\theta) & \\cos(\\theta) \\end{bmatrix}"
                ).scale(0.8).to_edge(UP)

                # 创建顶点坐标矩阵
                vertex_matrix = MathTex(
                    r"\\begin{bmatrix} 1 & 0 & -1 \\\\ 0 & 1 & 0 \\end{bmatrix}"
                ).scale(0.8).next_to(rotation_matrix, DOWN)

                # 展示坐标系和三角形
                self.play(Create(axes), Write(axes_labels))
                self.play(Create(triangle))
                self.wait(1)

                # 展示旋转矩阵和顶点坐标矩阵
                self.play(Write(rotation_matrix), Write(vertex_matrix))
                self.wait(2)

                # 旋转三角形
                self.play(
                    Rotate(triangle, angle=PI/2, about_point=ORIGIN),
                    run_time=3
                )
                self.wait(2)

                # 淡出所有对象
                self.play(
                    FadeOut(axes),
                    FadeOut(axes_labels),
                    FadeOut(triangle),
                    FadeOut(rotation_matrix),
                    FadeOut(vertex_matrix)
                )
                self.wait(1)

                # 镜头9: 矩阵缩放
                axes = Axes(
                    x_range=[-5, 5, 1],
                    y_range=[-5, 5, 1],
                    axis_config={"color": BLUE},
                )
                axes_labels = axes.get_axis_labels(x_label="x", y_label="y")
                self.play(Create(axes), Write(axes_labels))
                self.wait(1)

                # 创建正方形
                square = Polygon(
                    [1, 1, 0],
                    [1, -1, 0],
                    [-1, -1, 0],
                    [-1, 1, 0],
                    color=YELLOW
                )
                self.play(Create(square))
                self.wait(1)

                # 创建缩放矩阵
                scaling_matrix = Matrix([[2, 0], [0, 2]], h_buff=1.5)
                scaling_matrix_label = Tex("缩放矩阵").next_to(scaling_matrix, UP)
                self.play(Write(scaling_matrix), Write(scaling_matrix_label))
                self.wait(1)

                # 创建正方形的顶点坐标矩阵
                vertex_matrix = Matrix([[1, 1], [1, -1], [-1, -1], [-1, 1]], h_buff=1.5)
                vertex_matrix_label = Tex("顶点坐标矩阵").next_to(vertex_matrix, UP)
                self.play(Write(vertex_matrix), Write(vertex_matrix_label))
                self.wait(1)

                # 矩阵乘法运算
                result_matrix = Matrix([[2, 2], [2, -2], [-2, -2], [-2, 2]], h_buff=1.5)
                result_matrix_label = Tex("运算结果矩阵").next_to(result_matrix, UP)
                self.play(Transform(vertex_matrix, result_matrix), Transform(vertex_matrix_label, result_matrix_label))
                self.wait(1)

                # 正方形缩放
                scaled_square = Polygon(
                    [2, 2, 0],
                    [2, -2, 0],
                    [-2, -2, 0],
                    [-2, 2, 0],
                    color=YELLOW
                )
                self.play(Transform(square, scaled_square))
                self.wait(1)

                # 文字说明
                explanation = Tex("除了旋转，矩阵乘法还能实现缩放。\\\\\\\\就像这个正方形，与缩放矩阵相乘后，\\\\\\\\它就会按照相应的比例放大或缩小。\\\\\\\\可见，矩阵乘法在处理几何图形的变换时非常有用。").scale(0.8).to_edge(DOWN)
                self.play(Write(explanation))
                self.wait(2)

                # 淡出所有元素
                self.play(
                    FadeOut(axes),
                    FadeOut(axes_labels),
                    FadeOut(square),
                    FadeOut(scaling_matrix),
                    FadeOut(scaling_matrix_label),
                    FadeOut(result_matrix),
                    FadeOut(result_matrix_label),
                    FadeOut(explanation)
                )
                self.wait(1)

                # 镜头10: 总结矩阵乘法
                matrix_A = Matrix([[1, 2], [3, 4]]).scale(0.8).to_edge(LEFT)
                matrix_B = Matrix([[5, 6], [7, 8]]).scale(0.8).next_to(matrix_A, RIGHT, buff=1)
                matrix_C = Matrix([[19, 22], [43, 50]]).scale(0.8).next_to(matrix_B, RIGHT, buff=1)

                # 闪烁矩阵
                self.play(
                    Flash(matrix_A, color=YELLOW),
                    Flash(matrix_B, color=YELLOW),
                    Flash(matrix_C, color=YELLOW)
                )
                self.wait(1)

                # 显示总结文字
                summary_text = Tex(
                    "总结一下，矩阵乘法是对应元素相乘再相加，\\\\\\\\",
                    "结果矩阵的行数等于第一个矩阵的行数，\\\\\\\\",
                    "列数等于第二个矩阵的列数。\\\\\\\\",
                    "并且它在几何变换等领域有着重要的应用。\\\\\\\\",
                    "希望大家通过这个可视化的展示，\\\\\\\\",
                    "对矩阵相乘有更清晰的理解。"
                ).scale(0.7).to_edge(DOWN)

                self.play(Write(summary_text))
                self.wait(3)

                # 淡出所有内容
                self.play(
                    FadeOut(matrix_A),
                    FadeOut(matrix_B),
                    FadeOut(matrix_C),
                    FadeOut(summary_text)
                )
                self.wait(1)""");
    // 发送到RabbitMQ
    rabbitTemplate.convertAndSend(
        "direct_animation_task_exchange", "animation_task_2", animationTaskMessage);
  }
}
