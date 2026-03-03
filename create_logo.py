#!/usr/bin/env python3
"""
生成现代化的应用 Logo
- 渐变蓝色背景
- 圆角设计
- 白色图标
"""

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

def create_gradient(width, height, start_color, end_color):
    """创建渐变背景"""
    base = Image.new('RGB', (width, height), start_color)
    top = Image.new('RGB', (width, height), end_color)
    mask = Image.new('L', (width, height))
    mask_data = []
    for y in range(height):
        for x in range(width):
            # 从左上到右下的渐变
            progress = (x + y) / (width + height)
            mask_data.append(int(255 * progress))
    mask.putdata(mask_data)
    base.paste(top, (0, 0), mask)
    return base

def hex_to_rgb(hex_color):
    """十六进制颜色转 RGB"""
    hex_color = hex_color.lstrip('#')
    return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))

def create_rounded_rectangle_mask(size, radius):
    """创建圆角矩形蒙版"""
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    # 绘制圆角矩形
    draw.rounded_rectangle([(0, 0), size], radius=radius, fill=255)
    return mask

def create_modern_logo(size=1024):
    """创建现代化 Logo - 北极熊主题"""
    # 配色方案（基于 UI/UX 搜索结果）
    primary_color = hex_to_rgb('#3B82F6')  # 主蓝色
    secondary_color = hex_to_rgb('#60A5FA')  # 浅蓝色
    
    # 创建渐变背景
    img = create_gradient(size, size, primary_color, secondary_color)
    
    # 应用圆角
    radius = size // 4  # 25% 圆角（更圆润）
    mask = create_rounded_rectangle_mask((size, size), radius)
    
    # 应用蒙版
    img.putalpha(mask)
    
    # 创建绘图对象
    draw = ImageDraw.Draw(img)
    
    # 计算中心和缩放
    center = size // 2
    scale = size / 1024
    
    # 绘制北极熊（简洁轮廓风格）
    
    # 1. 身体（大椭圆）
    body_width = int(320 * scale)
    body_height = int(380 * scale)
    body_y = center + int(60 * scale)
    
    draw.ellipse(
        [(center - body_width//2, body_y - body_height//2),
         (center + body_width//2, body_y + body_height//2)],
        fill=(255, 255, 255, 240)
    )
    
    # 2. 头部（圆形）
    head_radius = int(180 * scale)
    head_y = center - int(140 * scale)
    
    draw.ellipse(
        [(center - head_radius, head_y - head_radius),
         (center + head_radius, head_y + head_radius)],
        fill=(255, 255, 255, 245)
    )
    
    # 3. 耳朵（两个小圆）
    ear_radius = int(50 * scale)
    ear_offset_x = int(120 * scale)
    ear_y = head_y - int(140 * scale)
    
    # 左耳
    draw.ellipse(
        [(center - ear_offset_x - ear_radius, ear_y - ear_radius),
         (center - ear_offset_x + ear_radius, ear_y + ear_radius)],
        fill=(255, 255, 255, 240)
    )
    
    # 右耳
    draw.ellipse(
        [(center + ear_offset_x - ear_radius, ear_y - ear_radius),
         (center + ear_offset_x + ear_radius, ear_y + ear_radius)],
        fill=(255, 255, 255, 240)
    )
    
    # 4. 眼睛（两个小圆点）
    eye_radius = int(16 * scale)
    eye_offset_x = int(60 * scale)
    eye_y = head_y - int(20 * scale)
    
    # 左眼
    draw.ellipse(
        [(center - eye_offset_x - eye_radius, eye_y - eye_radius),
         (center - eye_offset_x + eye_radius, eye_y + eye_radius)],
        fill=(59, 130, 246, 255)  # 蓝色眼睛
    )
    
    # 右眼
    draw.ellipse(
        [(center + eye_offset_x - eye_radius, eye_y - eye_radius),
         (center + eye_offset_x + eye_radius, eye_y + eye_radius)],
        fill=(59, 130, 246, 255)
    )
    
    # 5. 鼻子（小椭圆）
    nose_width = int(40 * scale)
    nose_height = int(28 * scale)
    nose_y = head_y + int(40 * scale)
    
    draw.ellipse(
        [(center - nose_width//2, nose_y - nose_height//2),
         (center + nose_width//2, nose_y + nose_height//2)],
        fill=(59, 130, 246, 255)
    )
    
    # 6. 手臂/爪子（两个椭圆）
    arm_width = int(100 * scale)
    arm_height = int(160 * scale)
    arm_offset_x = int(240 * scale)
    arm_y = body_y + int(20 * scale)
    
    # 左手臂
    draw.ellipse(
        [(center - arm_offset_x - arm_width//2, arm_y - arm_height//2),
         (center - arm_offset_x + arm_width//2, arm_y + arm_height//2)],
        fill=(255, 255, 255, 230)
    )
    
    # 右手臂
    draw.ellipse(
        [(center + arm_offset_x - arm_width//2, arm_y - arm_height//2),
         (center + arm_offset_x + arm_width//2, arm_y + arm_height//2)],
        fill=(255, 255, 255, 230)
    )
    
    # 7. 脚（两个椭圆）
    foot_width = int(120 * scale)
    foot_height = int(90 * scale)
    foot_offset_x = int(100 * scale)
    foot_y = body_y + int(220 * scale)
    
    # 左脚
    draw.ellipse(
        [(center - foot_offset_x - foot_width//2, foot_y - foot_height//2),
         (center - foot_offset_x + foot_width//2, foot_y + foot_height//2)],
        fill=(255, 255, 255, 235)
    )
    
    # 右脚
    draw.ellipse(
        [(center + foot_offset_x - foot_width//2, foot_y - foot_height//2),
         (center + foot_offset_x + foot_width//2, foot_y + foot_height//2)],
        fill=(255, 255, 255, 235)
    )
    
    # 添加微妙的内阴影效果（顶部高光）
    overlay = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    
    # 顶部高光
    for i in range(int(size * 0.3)):
        alpha = int(40 * (1 - i / (size * 0.3)))
        overlay_draw.ellipse(
            [(size//4 - i, -i), (3*size//4 + i, size//3 + i)],
            fill=(255, 255, 255, alpha)
        )
    
    img = Image.alpha_composite(img, overlay)
    
    return img

def main():
    """主函数"""
    print("🎨 开始生成 Logo...")
    
    # 生成 1024x1024 高分辨率版本
    logo = create_modern_logo(1024)
    output_path = 'everything-client/src/main/resources/images/app-icon-new.png'
    logo.save(output_path, 'PNG', optimize=True)
    print(f"✅ 高分辨率 Logo 已保存：{output_path}")
    
    # 预览尺寸
    preview = logo.resize((256, 256), Image.Resampling.LANCZOS)
    preview.save('everything-client/src/main/resources/images/app-icon-preview.png', 'PNG')
    print("✅ 预览版已保存：app-icon-preview.png")
    
    print("\n🎉 Logo 生成完成！")
    print(f"📏 尺寸：1024x1024px")
    print(f"🎨 风格：现代渐变 + 圆角设计")
    print(f"💙 配色：蓝色渐变（#3B82F6 → #60A5FA）")

if __name__ == '__main__':
    main()
