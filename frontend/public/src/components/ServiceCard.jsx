export default function ServiceCard({
  title,
  description,
  icon,
  onClick,
}) {
  return (
    <div
      className="service-card"
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          onClick();
        }
      }}
    >
      <div className="service-card-icon">
        {icon}
      </div>

      <div className="service-card-content">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>

      <div className="service-card-arrow">
        →
      </div>
    </div>
  );
}