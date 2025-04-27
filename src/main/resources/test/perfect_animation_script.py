from manim import *

class ManimScene(Scene):
    def construct(self):
        # 介绍线性变换
        title = Tex(r"线性变换的几何意义", tex_template=TexTemplateLibrary.ctex)
        title.scale(1.2).to_edge(UP)
        self.play(Write(title))
        self.wait(1)
        
        # 创建坐标系
        axes = Axes(
            x_range=[-5, 5, 1],
            y_range=[-5, 5, 1],
            axis_config={"color": BLUE},
            x_length=8,
            y_length=8
        )
        axes_labels = axes.get_axis_labels(x_label="x", y_label="y")
        
        # 展示坐标系
        self.play(Create(axes), Write(axes_labels))
        self.wait(1)
        
        # 创建基向量
        basis_i = Arrow(axes.get_origin(), axes.c2p(1, 0), buff=0, color=RED)
        basis_j = Arrow(axes.get_origin(), axes.c2p(0, 1), buff=0, color=GREEN)
        
        basis_i_label = Tex(r"$\vec{i}$").next_to(basis_i.get_end(), RIGHT*0.5)
        basis_j_label = Tex(r"$\vec{j}$").next_to(basis_j.get_end(), UP*0.5)
        
        basis_vectors = VGroup(basis_i, basis_j, basis_i_label, basis_j_label)
        
        # 展示基向量
        self.play(Create(basis_i), Create(basis_j))
        self.play(Write(basis_i_label), Write(basis_j_label))
        self.wait(1)
        
        # 解释基向量
        explanation1 = Tex(r"基向量 $\vec{i}$ 和 $\vec{j}$ 是标准正交基", tex_template=TexTemplateLibrary.ctex)
        explanation1.to_edge(DOWN).shift(UP)
        self.play(Write(explanation1))
        self.wait(2)
        self.play(FadeOut(explanation1))
        
        # 创建示例向量
        vec = Arrow(axes.get_origin(), axes.c2p(3, 2), buff=0, color=YELLOW)
        vec_label = Tex(r"$\vec{v} = 3\vec{i} + 2\vec{j}$").next_to(vec.get_end(), RIGHT)
        
        # 显示向量
        self.play(Create(vec), Write(vec_label))
        self.wait(1)
        
        # 解释向量的表示
        explanation2 = Tex(r"向量可以由基向量的线性组合表示", tex_template=TexTemplateLibrary.ctex)
        explanation2.to_edge(DOWN).shift(UP)
        self.play(Write(explanation2))
        self.wait(2)
        
        # 展示线性组合
        # x分量
        x_component = Arrow(axes.get_origin(), axes.c2p(3, 0), buff=0, color=RED_B)
        x_dashed = DashedLine(axes.c2p(3, 0), axes.c2p(3, 2), color=RED_B)
        x_label = Tex(r"$3\vec{i}$").next_to(x_component, DOWN)
        
        # y分量
        y_component = Arrow(axes.c2p(3, 0), axes.c2p(3, 2), buff=0, color=GREEN_B)
        y_dashed = DashedLine(axes.get_origin(), axes.c2p(3, 0), color=GREEN_B)
        y_label = Tex(r"$2\vec{j}$").next_to(y_component, RIGHT)
        
        # 显示分量
        self.play(
            Create(x_component), 
            Write(x_label),
            Create(x_dashed)
        )
        self.play(
            Create(y_component), 
            Write(y_label),
            Create(y_dashed)
        )
        self.wait(2)
        self.play(FadeOut(explanation2))
        
        # 引入变换矩阵
        self.play(
            FadeOut(x_component),
            FadeOut(y_component),
            FadeOut(x_dashed),
            FadeOut(y_dashed),
            FadeOut(x_label),
            FadeOut(y_label)
        )
        
        transform_explanation = Tex(r"现在我们应用线性变换 $A = \begin{bmatrix} 1 & 1 \\ 1 & 0 \end{bmatrix}$", tex_template=TexTemplateLibrary.ctex)
        transform_explanation.to_edge(DOWN).shift(UP)
        self.play(Write(transform_explanation))
        self.wait(2)
        
        # 变换基向量
        new_basis_i = Arrow(axes.get_origin(), axes.c2p(1, 1), buff=0, color=RED_E)
        new_basis_j = Arrow(axes.get_origin(), axes.c2p(1, 0), buff=0, color=GREEN_E)
        
        new_basis_i_label = Tex(r"$A\vec{i} = \begin{bmatrix} 1 \\ 1 \end{bmatrix}$").next_to(new_basis_i.get_end(), UR*0.5)
        new_basis_j_label = Tex(r"$A\vec{j} = \begin{bmatrix} 1 \\ 0 \end{bmatrix}$").next_to(new_basis_j.get_end(), RIGHT*0.5)
        
        # 变换基向量动画
        self.play(
            Transform(basis_i, new_basis_i),
            Transform(basis_i_label, new_basis_i_label)
        )
        self.wait(1)
        self.play(
            Transform(basis_j, new_basis_j),
            Transform(basis_j_label, new_basis_j_label)
        )
        self.wait(2)
        
        # 变换示例向量
        new_vec = Arrow(axes.get_origin(), axes.c2p(5, 3), buff=0, color=YELLOW_E)
        new_vec_label = Tex(r"$A\vec{v} = \begin{bmatrix} 5 \\ 3 \end{bmatrix}$").next_to(new_vec.get_end(), RIGHT)
        
        # 计算说明
        calc_explanation = Tex(r"$A\vec{v} = A(3\vec{i} + 2\vec{j}) = 3A\vec{i} + 2A\vec{j} = 3\begin{bmatrix} 1 \\ 1 \end{bmatrix} + 2\begin{bmatrix} 1 \\ 0 \end{bmatrix} = \begin{bmatrix} 5 \\ 3 \end{bmatrix}$")
        calc_explanation.scale(0.8).to_edge(DOWN).shift(UP)
        
        self.play(FadeOut(transform_explanation))
        self.play(Write(calc_explanation))
        self.wait(2)
        
        # 变换向量
        self.play(
            Transform(vec, new_vec),
            Transform(vec_label, new_vec_label)
        )
        self.wait(2)
        
        # 结论
        self.play(FadeOut(calc_explanation))
        conclusion = Tex(r"线性变换保持向量加法和标量乘法", tex_template=TexTemplateLibrary.ctex)
        conclusion.to_edge(DOWN).shift(UP)
        self.play(Write(conclusion))
        self.wait(2)
        
        # 淡出所有元素
        self.play(
            FadeOut(title),
            FadeOut(axes),
            FadeOut(axes_labels),
            FadeOut(basis_i),
            FadeOut(basis_j),
            FadeOut(basis_i_label),
            FadeOut(basis_j_label),
            FadeOut(vec),
            FadeOut(vec_label),
            FadeOut(conclusion)
        )
        self.wait(1)